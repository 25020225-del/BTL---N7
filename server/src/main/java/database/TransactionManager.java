package database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * Handles asynchronous database transaction tasks executing through bounded worker threads.
 * Prevents main network execution loop degradation under state persistence constraints.
 */
public class TransactionManager {

    private static final Logger log = LoggerFactory.getLogger(TransactionManager.class);

    private static final ExecutorService executor = Executors.newFixedThreadPool(5, r -> {
        Thread t = new Thread(r, "DB-Worker-Pool");
        t.setDaemon(true);
        return t;
    });

    /**
     * Enqueues an operation context for asynchronous execution inside the database worker pool.
     *
     * @param <T>  the evaluated computational result type
     * @param task the database process execution script block
     * @return a {@link CompletableFuture} tracked evaluation result
     */
    public static <T> CompletableFuture<T> submitTask(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();

        executor.submit(() -> {
            try {
                T result = task.call();
                future.complete(result);
            } catch (Exception e) {
                log.error("Error executing DB task: {}", e.getMessage(), e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * Triggers graceful termination sequence for the background persistence executor pool.
     */
    public static void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}