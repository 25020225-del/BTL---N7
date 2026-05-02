package controller;

import database.DatabaseManager;
import database.TransactionManager;
import model.user.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;

import static utils.ConsoleColors.*;

/**
 * Controller responsible for handling financial payment operations on the server side.
 * It primarily manages balance deposits and ensures that wallet updates and
 * transaction logging are performed atomically through the {@link TransactionManager}.
 */
public class ServerPaymentController {

    /**
     * Processes a successful deposit notification from a payment gateway (e.g., PayPal).
     * This method encapsulates the balance update and history logging into a single
     * ACID-compliant database transaction.
     *
     * @param user           The user whose wallet will be credited.
     * @param amountVND      The numerical value of the deposit in VND.
     * @param payPalOrderId  The external order identifier provided by PayPal for tracking.
     * @return {@code true} if the database transaction was successfully committed;
     *         {@code false} if an error occurred or the transaction was rolled back.
     */
    public boolean processDepositSuccess(User user, double amountVND, String payPalOrderId) {
        // Wrap deposit logic into a Task to add to the asynchronous database worker queue
        Callable<Boolean> depositTask = () -> {
            String updateWalletSql = "UPDATE wallets SET balance = balance + ? WHERE user_id = ?";
            String insertTxnSql = "INSERT INTO wallet_transactions (id, user_id, amount, description, created_at) VALUES (?, ?, ?, ?, ?)";

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false); // Start Database Transaction

                try {
                    String now = LocalDateTime.now().toString();

                    // 1. Update the current wallet balance
                    try (PreparedStatement ps = conn.prepareStatement(updateWalletSql)) {
                        ps.setDouble(1, amountVND);
                        ps.setString(2, user.getId());
                        ps.executeUpdate();
                    }

                    // 2. Persist the deposit record into the transaction history
                    try (PreparedStatement ps = conn.prepareStatement(insertTxnSql)) {
                        ps.setString(1, "DEP-" + System.currentTimeMillis());
                        ps.setString(2, user.getId());
                        ps.setDouble(3, amountVND);
                        ps.setString(4, "Deposit via PayPal (Order ID: " + payPalOrderId + ")");
                        ps.setString(5, now);
                        ps.executeUpdate();
                    }

                    conn.commit(); // Commit all changes as an atomic unit
                    System.out.println("[System]: \"" + YELLOW + user.getName() + RESET + "\" has deposited " + GREEN + amountVND + RESET + " VND");
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
        try {
            return TransactionManager.submitTask(depositTask).get();
        } catch (Exception e) {
            System.out.println("[System]: Cannot process deposit order: " + e.getMessage());
            return false;
        }
    }
}