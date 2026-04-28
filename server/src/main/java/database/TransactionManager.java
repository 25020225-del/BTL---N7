package database;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

import static utils.ConsoleColors.*;

public class TransactionManager {

    // The queue contains tasks that need to be written to the database
    private static final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private static final Thread dbWorker;

    static {
        // Create a single Worker Thread (Consumer)
        dbWorker = new Thread(() -> {
            System.out.println("[Database]: " + GREEN + "Transaction Worker Thread started." + RESET);
            while (true) {
                try {
                    // Remove the task from the queue (it will automatically wait if the queue is empty)
                    Runnable task = queue.take();
                    task.run();
                } catch (InterruptedException e) {
                    System.out.println("[Database]: Worker Thread interrupted.");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.out.println("[Database]: " + RED + "Error executing task: " + e.getMessage() + RESET);
                }
            }
        });

        // Set this process to run in the background so it doesn't interfere with the server shutdown process
        dbWorker.setDaemon(true);
        dbWorker.start();
    }

    /**
     * Receive a task (Callable) from ClientHandler, enqueue it
     * and return a CompletableFuture so that the client can wait for the result.
     */
    public static <T> CompletableFuture<T> submitTask(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();

        queue.offer(() -> {
            try {
                // Execute the task (at this point, only one thread is accessing SQLite)
                T result = task.call();
                future.complete(result); // Return a success result
            } catch (Exception e) {
                future.completeExceptionally(e); // Return any errors
            }
        });

        return future;
    }
}