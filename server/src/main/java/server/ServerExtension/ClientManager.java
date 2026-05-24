package server.ServerExtension;

import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ClientHandler;
import service.BroadcastManager;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static utils.ConsoleColors.BLUE;
import static utils.ConsoleColors.RESET;

/**
 * Manages all active client connections and coordinates communication across the server.
 *
 * <h2>Broadcast Architecture (Post-Refactor)</h2>
 * <p>
 * The legacy {@code broadcastPool} (fixed thread pool of 200 threads) has been replaced by
 * {@link BroadcastManager}, which provides a high-performance Pub/Sub engine with built-in
 * Debouncing and Batching. All general broadcasts now route through this engine.
 * </p>
 *
 * <h2>Two-tier Communication Model</h2>
 * <ul>
 *   <li><b>Targeted / unicast</b> ({@link #sendToUser}): Direct, fire-and-forget delivery to a
 *       specific user. Uses {@link BroadcastManager}'s I/O pool to avoid blocking caller threads,
 *       but bypasses debouncing because unicast messages must never be coalesced.</li>
 *   <li><b>General broadcast</b> ({@link #broadcast(String, Object, ClientHandler)}): Fan-out to
 *       all connected clients. Routed through {@link BroadcastManager} I/O pool for async
 *       non-blocking delivery.</li>
 *   <li><b>Auction room broadcast</b> ({@link #publishAuctionUpdate}): Fan-out to clients
 *       subscribed to a specific auction topic. Routed through {@link BroadcastManager#queueUpdate}
 *       to benefit from Debouncing + Batching (e.g., collapsing 1000 bids within 200 ms into
 *       one network write).</li>
 * </ul>
 *
 * <h2>Subscribe / Unsubscribe Lifecycle</h2>
 * <pre>
 *   Client opens auction view  →  subscribe(auctionId, client)
 *   Client closes / disconnects  →  unsubscribeAll(client)   ← NO memory leak
 * </pre>
 */
public class ClientManager {

    private static final Logger log = LoggerFactory.getLogger(ClientManager.class);

    // ── Registry ─────────────────────────────────────────────────────────────

    /** All currently-connected WebSocket sessions. CopyOnWriteArrayList gives safe iteration
     *  while connect/disconnect mutations happen concurrently. */
    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    /**
     * Reverse-index: client → set of auction topic IDs it has subscribed to.
     * Used by {@link #unsubscribeAll(ClientHandler)} to perform O(subscriptions) cleanup
     * on disconnect without iterating the full topics map inside BroadcastManager.
     *
     * Thread-safety: ConcurrentHashMap outer + ConcurrentHashMap.newKeySet() inner.
     */
    private static final ConcurrentHashMap<ClientHandler, Set<String>> clientSubscriptions =
            new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // CLIENT REGISTRY
    // ─────────────────────────────────────────────────────────────────────────

    public static void addClient(ClientHandler clientHandler) {
        clients.add(clientHandler);
        log.debug("Client added to registry: {}. Total: {}", clientHandler.getClientName(), clients.size());
    }

    /**
     * Removes the client from the connection registry and automatically unsubscribes it from
     * all auction topics it was watching. Call this on WebSocket onClose / forceDisconnect.
     */
    public static void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        // Cleanup all Pub/Sub subscriptions to prevent memory leaks in BroadcastManager.
        unsubscribeAll(clientHandler);
        log.debug("Client removed from registry: {}. Total: {}", clientHandler.getClientName(), clients.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUB/SUB SUBSCRIPTION MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Subscribes a client to receive real-time updates for a specific auction room.
     * Call this when the client sends a "JOIN_AUCTION" / "VIEW_AUCTION" command, or
     * immediately after a successful {@code PLACE_BID} so the bidder stays in the room.
     *
     * <p>Thread-safe: {@link BroadcastManager#subscribe} uses a ConcurrentHashMap-backed
     * set and this method updates the reverse-index atomically via {@code computeIfAbsent}.</p>
     *
     * @param auctionId The auction room topic to subscribe to.
     * @param client    The connecting client.
     */
    public static void subscribeToAuction(String auctionId, ClientHandler client) {
        // Register in BroadcastManager's topic map (the "push" side)
        BroadcastManager.subscribe(auctionId, client);

        // Record in our reverse-index (used for bulk unsubscribe on disconnect)
        clientSubscriptions
                .computeIfAbsent(client, k -> ConcurrentHashMap.newKeySet())
                .add(auctionId);

        log.debug("Client '{}' subscribed to auction topic '{}'.",
                client.getClientName(), auctionId);
    }

    /**
     * Unsubscribes a client from a specific auction room (e.g., client closes the room tab).
     *
     * @param auctionId The auction room topic to leave.
     * @param client    The client leaving the room.
     */
    public static void unsubscribeFromAuction(String auctionId, ClientHandler client) {
        BroadcastManager.unsubscribe(auctionId, client);

        Set<String> subs = clientSubscriptions.get(client);
        if (subs != null) {
            subs.remove(auctionId);
            // Clean up the reverse-index entry if the client has no more subscriptions
            if (subs.isEmpty()) {
                clientSubscriptions.remove(client);
            }
        }

        log.debug("Client '{}' unsubscribed from auction topic '{}'.",
                client.getClientName(), auctionId);
    }

    /**
     * Bulk-unsubscribes a client from every auction topic it was watching.
     * Called automatically by {@link #removeClient(ClientHandler)} on disconnect or timeout,
     * preventing stale {@link ClientHandler} references from leaking inside
     * {@link BroadcastManager}'s internal topic sets.
     *
     * @param client The disconnecting client.
     */
    public static void unsubscribeAll(ClientHandler client) {
        Set<String> subscriptions = clientSubscriptions.remove(client);
        if (subscriptions == null || subscriptions.isEmpty()) return;

        for (String auctionId : subscriptions) {
            BroadcastManager.unsubscribe(auctionId, client);
        }
        log.debug("Client '{}' fully unsubscribed from {} auction topic(s) on disconnect.",
                client.getClientName(), subscriptions.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUCTION-ROOM BROADCAST  (via BroadcastManager Debounce/Batch pipeline)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Queues an auction-state update for delivery to all subscribers of the given auction room.
     * The update is <em>debounced</em>: if multiple price changes arrive within the same 200 ms
     * batch window, only the latest snapshot is sent — dramatically reducing network write ops
     * during bid wars.
     *
     * <p>The caller is responsible for serializing {@code data} to a JSON string before calling
     * this method. Serialization should happen once at the call site (e.g., in
     * {@link controller.ServerBidderController}) to avoid redundant work inside the scheduler
     * thread.</p>
     *
     * <h3>Migration note</h3>
     * <pre>
     *   // OLD (fire-and-forget fan-out, no dedup):
     *   ClientManager.broadcast("UPDATE_AUCTION_PRICE", update, null);
     *
     *   // NEW (debounced room broadcast):
     *   String json = mapper.writeValueAsString(new NetworkMessage("UPDATE_AUCTION_PRICE", update));
     *   ClientManager.publishAuctionUpdate(auctionId, json);
     * </pre>
     *
     * @param auctionId   The auction room topic key.
     * @param jsonPayload Pre-serialized JSON payload string.
     */
    public static void publishAuctionUpdate(String auctionId, String jsonPayload) {
        BroadcastManager.queueUpdate(auctionId, jsonPayload);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GENERAL BROADCAST  (all clients, via BroadcastManager I/O pool)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends a plain-text chat message to all clients except the sender.
     * Uses {@link BroadcastManager}'s I/O thread pool — no 200-thread pool required.
     */
    public static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                BroadcastManager.dispatchDirect(client, () -> {
                    try {
                        client.sendMessage(message);
                    } catch (Exception e) {
                        log.error("Error broadcasting to {}: {}", client.getClientName(), e.getMessage());
                    }
                });
            }
        }
    }

    /**
     * Sends a {@code NetworkMessage} (command + data) to all clients except the sender.
     * Uses {@link BroadcastManager}'s I/O thread pool.
     */
    public static void broadcast(String command, Object data, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                BroadcastManager.dispatchDirect(client, () -> {
                    try {
                        client.sendResponse(command, data);
                    } catch (Exception e) {
                        log.error("Cannot broadcast to {}", client.getClientName());
                    }
                });
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TARGETED MESSAGING
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends a private admin message to a client by username.
     * Silent-miss policy: no-op if the user is offline.
     */
    public static void privateMsg(String receiver, String message) {
        receiver = receiver.trim();
        for (ClientHandler client : clients) {
            if (client.getClientName() != null && client.getClientName().equals(receiver)) {
                client.sendMessage("[Admin]" + BLUE + "(private)" + RESET + ": " + message);
                return;
            }
        }
        System.out.println("[System]: User \"" + receiver + "\" doesn't exist");
    }

    /**
     * Sends a targeted response to a specific user by userId.
     *
     * <p>Unicast messages are dispatched immediately through {@link BroadcastManager}'s I/O pool
     * to avoid blocking the DB worker thread — but they bypass the debounce queue because unicast
     * delivery must never be coalesced with other users' updates.</p>
     *
     * <p>Silent-miss policy: if the user is offline, the notification is dropped gracefully.</p>
     *
     * @param userId  The target user's database ID.
     * @param command The network command to send.
     * @param data    The payload to serialize.
     */
    public static void sendToUser(String userId, String command, Object data) {
        for (ClientHandler client : clients) {
            User user = client.getUser();
            if (user != null && user.getId().equals(userId)) {
                // Use BroadcastManager's I/O pool for non-blocking unicast delivery.
                BroadcastManager.dispatchDirect(client, () -> {
                    try {
                        client.sendResponse(command, data);
                        log.debug("Push notification sent to user {}: command={}", userId, command);
                    } catch (Exception e) {
                        log.error("Failed to send notification to user {}: {}", userId, e.getMessage());
                    }
                });
                return;
            }
        }
        log.debug("sendToUser: user {} is offline. Notification '{}' dropped.", userId, command);
    }

    /**
     * Immediately updates the in-memory {@link User} role for a connected client,
     * so {@code isBlocked()} takes effect instantly without a reconnect.
     */
    public static void updateBlockStatusInMemory(String userId, boolean isBlocked) {
        for (ClientHandler client : clients) {
            User user = client.getUser();
            if (user != null && user.getId().equals(userId)) {
                if (isBlocked) {
                    user.setRole("BLOCKED");
                    log.info("[BLOCK] In-memory role updated to BLOCKED for user '{}'.", userId);
                } else {
                    user.setRole("USER");
                    log.info("[UNBLOCK] In-memory role restored to USER for user '{}'.", userId);
                }
                return;
            }
        }
        log.debug("[BLOCK] User '{}' is offline — in-memory update skipped.", userId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADMIN CONSOLE COMMANDS
    // ─────────────────────────────────────────────────────────────────────────

    /** Kicks a client by username. */
    public static void kickTarget(String target, String reason) {
        for (ClientHandler client : clients) {
            if (target.equals(client.getClientName())) {
                client.forceDisconnect(reason);
                log.info("kickTarget: User '{}' has been kicked. Reason: {}", target, reason);
                return;
            }
        }
        log.warn("kickTarget: User '{}' not found.", target);
    }

    /** Kicks a client by 1-based index in the connection list. */
    public static void kickTargetByNumber(int index, String reason) {
        int zeroBasedIndex = index - 1;
        if (zeroBasedIndex < 0 || zeroBasedIndex >= clients.size()) {
            log.warn("kickTargetByNumber: Index {} out of range (total clients: {}).", index, clients.size());
            System.out.println("[System]: Invalid client index: " + index);
            return;
        }
        ClientHandler target = clients.get(zeroBasedIndex);
        target.forceDisconnect(reason);
        log.info("kickTargetByNumber: Client #{} ('{}') kicked. Reason: {}", index, target.getClientName(), reason);
    }

    /** Prints the current client list to the admin console. */
    public static void getClientList() {
        if (clients.isEmpty()) {
            System.out.println("[System]: No clients connected.");
            return;
        }
        System.out.println("=== Connected Clients (" + clients.size() + ") ===");
        for (int i = 0; i < clients.size(); i++) {
            ClientHandler client = clients.get(i);
            User user = client.getUser();
            String identity = (user != null) ? user.getUserName() : "(not logged in)";
            System.out.printf("  [%d] Name: %-20s | Identity: %s%n",
                    i + 1, client.getClientName(), identity);
        }
        System.out.println("==========================================");
    }

    /** Instructs a client's GUI to open a URL. */
    public static void redirectClient(String target, String url) {
        for (ClientHandler client : clients) {
            if (target.equals(client.getClientName())) {
                client.redirectToWebsite(url);
                log.info("redirectClient: User '{}' redirected to '{}'.", target, url);
                return;
            }
        }
        log.warn("redirectClient: User '{}' not found.", target);
        System.out.println("[System]: User \"" + target + "\" not found for redirect.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gracefully shuts down the broadcast engine.
     * Must be called from the JVM Shutdown Hook in {@code MultiThreadedServer}.
     *
     * <p>Delegation order matters:</p>
     * <ol>
     *   <li>Tell {@link BroadcastManager} to stop — this halts the 200 ms batch ticker and
     *       drains the I/O pool, ensuring the last in-flight writes complete.</li>
     *   <li>Clear the reverse-index to release {@link ClientHandler} references.</li>
     * </ol>
     */
    public static void shutdown() {
        log.info("ClientManager: Delegating shutdown to BroadcastManager...");
        BroadcastManager.shutdown();
        clientSubscriptions.clear();
        log.info("ClientManager: Shutdown complete.");
    }
}