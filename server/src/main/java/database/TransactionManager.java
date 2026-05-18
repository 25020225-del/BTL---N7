package database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * Manages asynchronous database transaction execution using a ThreadPool worker pattern.
 * Leverages SQLite's WAL mode and HikariCP connection pooling to allow
 * concurrent database operations across different auctions.
 */
public class TransactionManager {

    private static final Logger log = LoggerFactory.getLogger(TransactionManager.class);

    /**
     * Fixed Thread Pool to handle concurrent database tasks.
     * Size matches the maximum HikariCP connection pool size to prevent connection starvation.
     */
    private static final ExecutorService executor = Executors.newFixedThreadPool(5, r -> {
        Thread t = new Thread(r, "DB-Worker-Pool");
        t.setDaemon(true);
        return t;
    });

    /**
     * Submits a database task for asynchronous execution and returns a future result.
     *
     * @param <T>  The type of the result produced by the task.
     * @param task The logic to be executed by the database worker thread.
     * @return A {@link CompletableFuture} that completes once a worker thread finishes the task.
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
     * Gracefully shuts down the database executor, waiting up to 60 seconds for tasks to finish.
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