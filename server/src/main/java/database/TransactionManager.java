package database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

import static utils.ConsoleColors.*;

/**
 * Manages asynchronous database transaction execution using a single-threaded worker pattern.
 * This class ensures that all write operations to the SQLite database are serialized
 * (executed one after another) to prevent "Database is locked" errors, as SQLite
 * does not natively support high-concurrency writes.
 */
public class TransactionManager {
    private static Logger log = LoggerFactory.getLogger(TransactionManager.class);

    /**
     * Thread-safe queue containing database tasks awaiting execution.
     * Implements the Producer-Consumer pattern.
     */
    private static final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

    /**
     * The single background thread dedicated to consuming and running database tasks.
     */
    private static final Thread dbWorker;

    static {
        // Initialize the dedicated Database Worker Thread
        dbWorker = new Thread(() -> {
            log.info("Transaction Worker Thread started.");
            while (true) {
                try {
                    // Block and wait for the next available task in the queue
                    Runnable task = queue.take();
                    task.run();
                } catch (InterruptedException e) {
                    log.error("Worker Thread interrupted.");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error executing task: {}", e.getMessage());
                }
            }
        });

        // Set the worker as a daemon to ensure it doesn't block the JVM from shutting down
        dbWorker.setDaemon(true);
        dbWorker.start();
    }

    /**
     * Submits a database task for asynchronous execution and returns a future result.
     * This method acts as the 'Producer'. It wraps a {@link Callable} into a {@link Runnable},
     * adds it to the internal queue, and provides a {@link CompletableFuture} that the
     * caller can use to retrieve the result or handle errors.
     *
     * @param <T>  The type of the result produced by the task.
     * @param task The logic to be executed by the database worker thread.
     * @return A {@link CompletableFuture} that will be completed once the worker finishes the task.
     */
    public static <T> CompletableFuture<T> submitTask(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();

        // Enqueue the task; execution will happen on the dbWorker thread
        queue.offer(() -> {
            try {
                // Execute the task logic
                T result = task.call();
                // Signal success to the future
                future.complete(result);
            } catch (Exception e) {
                // Signal failure to the future
                future.completeExceptionally(e);
            }
        });

        return future;
    }
}