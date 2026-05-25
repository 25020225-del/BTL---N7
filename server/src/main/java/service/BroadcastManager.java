package service;

import server.ClientHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Enterprise real-time pub-sub broadcast coordinator.
 * Employs micro-batched windows and atomic payload overwrites to mitigate networking contention.
 */
public class BroadcastManager {

    private static final Map<String, Set<ClientHandler>> topics = new ConcurrentHashMap<>();
    private static final Map<String, String> pendingPayloads = new ConcurrentHashMap<>();
    private static final ExecutorService networkIoPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    private static final ScheduledExecutorService batchScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Broadcast-Batch-Ticker");
                t.setDaemon(true);
                return t;
            });

    static {
        batchScheduler.scheduleAtFixedRate(BroadcastManager::flushPayloads, 0, 200, TimeUnit.MILLISECONDS);
    }

    public static void subscribe(String auctionId, ClientHandler client) {
        topics.computeIfAbsent(auctionId, k -> ConcurrentHashMap.newKeySet()).add(client);
    }

    public static void unsubscribe(String auctionId, ClientHandler client) {
        Set<ClientHandler> subscribers = topics.get(auctionId);
        if (subscribers != null) {
            subscribers.remove(client);
            if (subscribers.isEmpty()) {
                topics.remove(auctionId);
            }
        }
    }

    /**
     * Stashes a pre-serialized update envelope inside the map queue, debouncing older un-flushed records.
     *
     * @param auctionId   routing pub-sub room index key
     * @param jsonPayload pre-serialized static transport message block
     */
    public static void queueUpdate(String auctionId, String jsonPayload) {
        pendingPayloads.put(auctionId, jsonPayload);
    }

    public static void dispatchDirect(ClientHandler client, Runnable task) {
        networkIoPool.submit(task);
    }

    private static void flushPayloads() {
        if (pendingPayloads.isEmpty()) return;

        Map<String, String> snapshot;
        // Synchronizing over the map lock guarantees a concurrent queueUpdate landing during the drain sequence is preserved safely for the next heartbeat window
        synchronized (pendingPayloads) {
            snapshot = new ConcurrentHashMap<>(pendingPayloads);
            pendingPayloads.clear();
        }

        snapshot.forEach((auctionId, payload) -> {
            Set<ClientHandler> subscribers = topics.get(auctionId);
            if (subscribers != null && !subscribers.isEmpty()) {
                networkIoPool.submit(() -> {
                    for (ClientHandler client : subscribers) {
                        try {
                            client.sendPreSerializedResponse(payload);
                        } catch (Exception e) {
                            unsubscribe(auctionId, client);
                        }
                    }
                });
            }
        });
    }

    public static void shutdown() {
        batchScheduler.shutdownNow();
        networkIoPool.shutdown();
        try {
            if (!networkIoPool.awaitTermination(5, TimeUnit.SECONDS)) {
                networkIoPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            networkIoPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("BroadcastManager: Shutdown complete.");
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BroadcastManager.class);
}