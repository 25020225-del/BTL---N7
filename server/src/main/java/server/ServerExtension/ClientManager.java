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
 * Central connection registry orchestrating state mapping, real-time messaging,
 * and topic-based Pub/Sub lifecycles for active WebSocket sessions.
 */
public class ClientManager {

    private static final Logger log = LoggerFactory.getLogger(ClientManager.class);
    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private static final ConcurrentHashMap<ClientHandler, Set<String>> clientSubscriptions = new ConcurrentHashMap<>();

    public static void addClient(ClientHandler clientHandler) {
        clients.add(clientHandler);
        log.debug("Client added to registry: {}. Total: {}", clientHandler.getClientName(), clients.size());
    }

    /**
     * Evicts a client session from the registry and issues automatic multi-topic un-registrations
     * to eliminate connection references and safeguard memory boundaries.
     *
     * @param clientHandler targeted active session handler instance
     */
    public static void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        unsubscribeAll(clientHandler);
        log.debug("Client removed from registry: {}. Total: {}", clientHandler.getClientName(), clients.size());
    }

    /**
     * Encapsulates connection references inside a structured auction publisher channel topic.
     *
     * @param auctionId unique targeting room identity string
     * @param client    subscribing request driver context
     */
    public static void subscribeToAuction(String auctionId, ClientHandler client) {
        BroadcastManager.subscribe(auctionId, client);
        clientSubscriptions
                .computeIfAbsent(client, k -> ConcurrentHashMap.newKeySet())
                .add(auctionId);
        log.debug("Client '{}' subscribed to auction topic '{}'.", client.getClientName(), auctionId);
    }

    /**
     * Explicitly detaches a client context session from a single active auction room topic.
     */
    public static void unsubscribeFromAuction(String auctionId, ClientHandler client) {
        BroadcastManager.unsubscribe(auctionId, client);
        Set<String> subs = clientSubscriptions.get(client);
        if (subs != null) {
            subs.remove(auctionId);
            if (subs.isEmpty()) {
                clientSubscriptions.remove(client);
            }
        }
        log.debug("Client '{}' unsubscribed from auction topic '{}'.", client.getClientName(), auctionId);
    }

    /**
     * Flushes entire subscription references bound to an identity handler session instance.
     */
    public static void unsubscribeAll(ClientHandler client) {
        Set<String> subscriptions = clientSubscriptions.remove(client);
        if (subscriptions == null || subscriptions.isEmpty()) return;

        for (String auctionId : subscriptions) {
            BroadcastManager.unsubscribe(auctionId, client);
        }
        log.debug("Client '{}' fully unsubscribed from {} auction topic(s) on disconnect.", client.getClientName(), subscriptions.size());
    }

    /**
     * Forwards serialized JSON payload packages into low-level batched, debounced dispatch pipelines.
     *
     * @param auctionId   target context routing index key
     * @param jsonPayload atomic optimized messaging string block
     */
    public static void publishAuctionUpdate(String auctionId, String jsonPayload) {
        BroadcastManager.queueUpdate(auctionId, jsonPayload);
    }

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
     * Issues an isolated, un-batched direct transport notification message toward a targeted identity context.
     *
     * @param userId  unique destination target user identity
     * @param command remote action execution directive route header
     * @param data    un-serialized transport state payload element
     */
    public static void sendToUser(String userId, String command, Object data) {
        for (ClientHandler client : clients) {
            User user = client.getUser();
            if (user != null && user.getId().equals(userId)) {
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
     * Forces real-time in-memory status role modifications over active clients to intercept malicious requests instantly.
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
            System.out.printf("  [%d] Name: %-20s | Identity: %s%n", i + 1, client.getClientName(), identity);
        }
        System.out.println("==========================================");
    }

    public static void redirectClient(String target, String url) {
        for (ClientHandler client : clients) {
            if (target.equals(client.getClientName())) {
                client.redirectToWebsite(url);
                log.info("redirectClient: User '{}' redirected to '{}'.", target, url);
                return;
            }
        }
        log.warn("redirectClient: User '{}' not found.", target);
        System.out.println("[System]: User \\\"\" + target + \"\\\" not found for redirect.");
    }

    /**
     * Executes resource termination sequences across structural core broadcasting dependencies.
     */
    public static void shutdown() {
        log.info("ClientManager: Delegating shutdown to BroadcastManager...");
        BroadcastManager.shutdown();
        clientSubscriptions.clear();
        log.info("ClientManager: Shutdown complete.");
    }
}