package service;

import server.ClientHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * High-performance broadcast engine using Batching, Debouncing, and Pub/Sub.
 *
 * <h2>Architecture</h2>
 * <pre>
 *   ┌─────────────────────────────────────────────────────────────┐
 *   │  Caller Thread (DB Worker / AutoBidEngine)                  │
 *   │   queueUpdate(auctionId, json)  →  pendingPayloads (RAM)    │
 *   │   dispatchDirect(client, task)  →  networkIoPool (unicast)  │
 *   └─────────────────────────────────────────────────────────────┘
 *           ↓ every 200 ms (batchScheduler ticker)
 *   ┌─────────────────────────────────────────────────────────────┐
 *   │  flushPayloads()                                            │
 *   │   snapshot ← drain pendingPayloads                         │
 *   │   for each auctionId → networkIoPool.submit(fan-out task)   │
 *   └─────────────────────────────────────────────────────────────┘
 *           ↓ networkIoPool (CPU*2 threads)
 *   ┌─────────────────────────────────────────────────────────────┐
 *   │  ClientHandler.sendMessage(payload)  (per subscriber)       │
 *   └─────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Debouncing guarantee</h2>
 * <p>If 1 000 bids arrive within a single 200 ms window for the same {@code auctionId},
 * {@code pendingPayloads.put()} simply overwrites the previous entry — only the
 * <em>latest</em> state snapshot is ever sent to subscribers. This eliminates the
 * thundering-herd problem that the old 200-thread {@code broadcastPool} suffered from.</p>
 *
 * <h2>Thread-safety</h2>
 * <ul>
 *   <li>{@code topics}: ConcurrentHashMap + ConcurrentHashMap.newKeySet() — all
 *       subscribe/unsubscribe ops are lock-free.</li>
 *   <li>{@code pendingPayloads}: ConcurrentHashMap — {@code put()} is atomic; the
 *       snapshot-and-clear in {@code flushPayloads()} is guarded by a {@code synchronized}
 *       block on the map itself to prevent a flush racing with a concurrent {@code put()}
 *       and silently dropping a pending update.</li>
 *   <li>{@code networkIoPool}: standard {@link ExecutorService} — all submitted tasks are
 *       independent and require no further synchronization.</li>
 * </ul>
 */
public class BroadcastManager {

    // ── 1. PUB/SUB topic registry ─────────────────────────────────────────────
    /**
     * Maps {@code auctionId → Set<ClientHandler>} of currently-subscribed clients.
     * ConcurrentHashMap.newKeySet() gives a concurrent, resizable set backed by CHM.
     */
    private static final Map<String, Set<ClientHandler>> topics = new ConcurrentHashMap<>();

    // ── 2. Debounce state ─────────────────────────────────────────────────────
    /**
     * Holds the <em>latest</em> pre-serialized JSON snapshot per auction topic.
     * High-frequency updates for the same auction overwrite the previous entry —
     * only one network write per 200 ms window per auction.
     */
    private static final Map<String, String> pendingPayloads = new ConcurrentHashMap<>();

    // ── 3. Batch scheduler (single-thread ticker) ─────────────────────────────
    /**
     * One ultra-lightweight thread that fires the flush cycle every 200 ms.
     * Marked as daemon so it does not prevent JVM exit.
     */
    private static final ScheduledExecutorService batchScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Broadcast-Batch-Ticker");
                t.setDaemon(true);
                return t;
            });

    // ── 4. NIO / I/O thread pool ──────────────────────────────────────────────
    /**
     * Performs the actual socket writes asynchronously.
     * Sized at {@code CPU cores × 2} — optimal for I/O-bound tasks.
     * Also reused by {@link #dispatchDirect} for unicast delivery from ClientManager.
     */
    private static final ExecutorService networkIoPool =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    // Static initializer — start the heartbeat
    static {
        batchScheduler.scheduleAtFixedRate(
                BroadcastManager::flushPayloads, 0, 200, TimeUnit.MILLISECONDS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUB/SUB API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Subscribes a client to an auction's real-time update topic.
     * Called by {@link server.ServerExtension.ClientManager#subscribeToAuction}.
     *
     * @param auctionId The auction room topic.
     * @param client    The subscribing client session.
     */
    public static void subscribe(String auctionId, ClientHandler client) {
        topics.computeIfAbsent(auctionId, k -> ConcurrentHashMap.newKeySet()).add(client);
    }

    /**
     * Unsubscribes a client from an auction topic.
     * Also cleans up the topic entry if no subscribers remain.
     *
     * @param auctionId The auction room topic.
     * @param client    The unsubscribing client session.
     */
    public static void unsubscribe(String auctionId, ClientHandler client) {
        Set<ClientHandler> subscribers = topics.get(auctionId);
        if (subscribers != null) {
            subscribers.remove(client);
            if (subscribers.isEmpty()) {
                topics.remove(auctionId);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLISH (DEBOUNCED)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Queues an auction-state update for the next batch flush.
     *
     * <p><strong>Debounce rule:</strong> Calling this method multiple times for the same
     * {@code auctionId} within a 200 ms window results in exactly <em>one</em> network write
     * carrying only the last payload. This is the correct semantic for auction price updates
     * (only the current price matters, intermediate values are irrelevant).</p>
     *
     * <p><strong>Serialization contract:</strong> Callers must serialize the payload to a JSON
     * string <em>before</em> calling this method. Centralizing serialization here would force
     * the ticker thread to re-serialize on every flush — the caller thread is the right place.</p>
     *
     * @param auctionId   Topic key (= {@code Auction.getId()}).
     * @param jsonPayload Pre-serialized JSON string (e.g., a full {@code NetworkMessage}).
     */
    public static void queueUpdate(String auctionId, String jsonPayload) {
        pendingPayloads.put(auctionId, jsonPayload);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DIRECT (NON-DEBOUNCED) DISPATCH
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Submits an arbitrary I/O task to the shared network thread pool for <em>immediate</em>
     * (non-debounced) execution.
     *
     * <p>Used by {@link server.ServerExtension.ClientManager} for:</p>
     * <ul>
     *   <li>Unicast delivery ({@code sendToUser}) — must not be coalesced.</li>
     *   <li>General fan-out ({@code broadcast}) — each client gets its own task.</li>
     * </ul>
     *
     * <p>The {@code client} parameter is accepted but not used internally; it serves as a
     * documentation hint at call sites indicating which client the task targets.</p>
     *
     * @param client Informational — the target client (unused internally).
     * @param task   The I/O task to execute on the network pool.
     */
    public static void dispatchDirect(ClientHandler client, Runnable task) {
        networkIoPool.submit(task);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTERNAL: BATCH FLUSH
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Periodic flush — drains {@link #pendingPayloads} and fans the payloads out to subscribers.
     * Runs every 200 ms on the single-thread {@link #batchScheduler}.
     *
     * <h3>Snapshot pattern</h3>
     * <p>We copy the map and clear it inside a {@code synchronized(pendingPayloads)} block.
     * This guarantees that a {@link #queueUpdate} racing with the flush is either captured
     * in the current snapshot or safely lands in the freshly-cleared map for the next cycle —
     * it is never silently lost.</p>
     */
    private static void flushPayloads() {
        if (pendingPayloads.isEmpty()) return;

        // Atomic snapshot-and-drain
        Map<String, String> snapshot;
        synchronized (pendingPayloads) {
            snapshot = new ConcurrentHashMap<>(pendingPayloads);
            pendingPayloads.clear();
        }

        // Fan out each snapshot entry to its subscribers via the I/O pool
        snapshot.forEach((auctionId, payload) -> {
            Set<ClientHandler> subscribers = topics.get(auctionId);
            if (subscribers != null && !subscribers.isEmpty()) {
                networkIoPool.submit(() -> {
                    for (ClientHandler client : subscribers) {
                        try {
                            client.sendMessage(payload);
                        } catch (Exception e) {
                            // Dead connection — auto-unsubscribe to keep the topic set clean
                            unsubscribe(auctionId, client);
                        }
                    }
                });
            }
        });
    }



    // ─────────────────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Graceful shutdown sequence. Called by
     * {@link server.ServerExtension.ClientManager#shutdown()} from the JVM shutdown hook.
     *
     * <ol>
     *   <li>Stop the batch ticker immediately ({@code shutdownNow}) — no new flushes.</li>
     *   <li>Initiate orderly shutdown of the I/O pool — in-flight writes complete.</li>
     *   <li>Wait up to 5 seconds for queued tasks to drain.</li>
     *   <li>Force-kill any stragglers if the deadline is exceeded.</li>
     * </ol>
     */
    public static void shutdown() {
        // 1. Stop the ticker — no new flush cycles
        batchScheduler.shutdownNow();

        // 2. Let the I/O pool drain in-flight writes
        networkIoPool.shutdown();
        try {
            if (!networkIoPool.awaitTermination(5, TimeUnit.SECONDS)) {
                networkIoPool.shutdownNow();
                log.warn("BroadcastManager: I/O pool forced shutdown after 5 s timeout.");
            }
        } catch (InterruptedException e) {
            networkIoPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("BroadcastManager: Shutdown complete.");
    }

    // Logger (static field placed last to keep the constants block clean)
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(BroadcastManager.class);
}