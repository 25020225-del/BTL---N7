package controller;

import database.DatabaseManager;
import database.TransactionManager;
import database.dao.WalletDAO;
import model.user.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static utils.ConsoleColors.*;

/**
 * Controller responsible for handling financial payment operations on the server side.
 * It primarily manages balance deposits and ensures that wallet updates and
 * transaction logging are performed atomically through the {@link TransactionManager}.
 */
public class ServerPaymentController {
    private final WalletDAO walletDAO = new WalletDAO();

    /**
     * Processes a successful deposit notification from a payment gateway (e.g., PayPal).
     * Encapsulates the balance update and history logging into a single ACID transaction.
     *
     * @param user          The user whose wallet will be credited.
     * @param payPalOrderId The external order identifier provided by PayPal for tracking.
     * @return A {@link CompletableFuture} resolving to true if the transaction succeeds.
     */
    public CompletableFuture<Boolean> processDepositSuccess(User user, String payPalOrderId) {
        // Wrap deposit logic into a Task to add to the asynchronous database worker queue
        Callable<Boolean> depositTask = () -> {
            // Verify the transaction with PayPal API (Mock)
            double verifiedAmount = verifyPayPalTransaction(payPalOrderId);

            if (verifiedAmount <= 0) {
                System.out.println("[Security]: " + RED + "Payment verification failed for Order ID: " + payPalOrderId + RESET);
                return false;
            }

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false); // Start Database Transaction

                try {
                    String now = LocalDateTime.now().toString();

                    // 1. Update the current wallet balance
                    walletDAO.updateBalance(conn, user.getId(), verifiedAmount);

                    // 2. Persist the deposit record into the transaction history
                    walletDAO.addTransaction(
                            conn,
                            "DEP-" + System.currentTimeMillis(),
                            user.getId(),
                            verifiedAmount,
                            "Deposit via PayPal (Order ID: " + payPalOrderId + ")",
                            now
                    );

                    conn.commit(); // Commit all changes as an atomic unit
                    System.out.println("[System]: \"" + YELLOW + user.getName() + RESET + "\" has deposited " + GREEN + verifiedAmount + RESET + " VND (Verified)");
                    return true;

                } catch (SQLException e) {
                    conn.rollback(); // Undo changes if any step fails
                    System.out.println("[Database]: Deposit update error: " + RED + e.getMessage() + RESET);
                    return false;
                }
            } catch (SQLException e) {
                System.out.println("[Database]: Lost connection to database: " + RED + e.getMessage() + RESET);
                return false;
            }
        };

        // Submit the task to the TransactionManager and wait for the synchronous result
        return TransactionManager.submitTask(depositTask);
    }

    /**
     * Mocks an external PayPal API call to verify the actual amount paid for an order.
     * This prevents "Price Manipulation" attacks where a client could manually send
     * a fake amount to the server.
     *
     * @param orderId The PayPal Order ID to verify.
     * @return The verified amount in VND.
     */
    private double verifyPayPalTransaction(String orderId) {
        try {
            // Simulate network latency for API call
            Thread.sleep(1000);

            // Mock verification logic: 
            // In a real app, this would use PayPal SDK to fetch order details.
            // For this project, we return a fixed set of valid amounts based on orderId length
            // or just random valid amounts for demonstration.
            double[] validAmounts = {50000, 100000, 200000, 500000};
            int index = Math.abs(orderId.hashCode()) % validAmounts.length;
            return validAmounts[index];

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }
}