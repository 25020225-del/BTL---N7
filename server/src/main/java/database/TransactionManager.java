package database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * Manages asynchronous database transaction execution using a ThreadPool worker pattern.
 * This class leverages SQLite's WAL mode and HikariCP connection pooling to allow
 * concurrent database operations across different auctions.
 */
public class TransactionManager {
    private static Logger log = LoggerFactory.getLogger(TransactionManager.class);

    /**
     * Fixed Thread Pool to handle concurrent database tasks.
     * The size is set to 5 to match the maximum connection pool size of HikariCP.
     */
    private static final ExecutorService executor = Executors.newFixedThreadPool(5, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("DB-Worker-Pool");
        return t;
    });

    /**
     * Submits a database task for asynchronous execution and returns a future result.
     *
     * @param <T>  The type of the result produced by the task.
     * @param task The logic to be executed by the database worker thread.
     * @return A {@link CompletableFuture} that will be completed once a thread in the pool finishes the task.
     */
    public static <T> CompletableFuture<T> submitTask(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();

        executor.submit(() -> {
            try {
                // Execute the task logic
                T result = task.call();
                // Signal success to the future
                future.complete(result);
            } catch (Exception e) {
                log.error("Error executing DB task: {}", e.getMessage());
                // Signal failure to the future
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * Gracefully shuts down the database executor.
     */
    public static void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}