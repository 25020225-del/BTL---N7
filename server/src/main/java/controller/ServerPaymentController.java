package controller;

import database.DatabaseManager;
import database.TransactionManager;
import model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;

import static utils.ConsoleColors.*;

public class ServerPaymentController {

    public boolean processDepositSuccess(User user, double amountVND, String payPalOrderId) {
        // Wrap deposit logic into a Task to add to the queue
        Callable<Boolean> depositTask = () -> {
            String updateWalletSql = "UPDATE wallets SET balance = balance + ? WHERE user_id = ?";
            String insertTxnSql = "INSERT INTO wallet_transactions (id, user_id, amount, description, created_at) VALUES (?, ?, ?, ?, ?)";

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false); // Start Transaction

                try {
                    String now = LocalDateTime.now().toString();

                    // 1. Update balance
                    try (PreparedStatement ps = conn.prepareStatement(updateWalletSql)) {
                        ps.setDouble(1, amountVND);
                        ps.setString(2, user.getId());
                        ps.executeUpdate();
                    }

                    // 2. Save deposit history into database
                    try (PreparedStatement ps = conn.prepareStatement(insertTxnSql)) {
                        ps.setString(1, "DEP-" + System.currentTimeMillis());
                        ps.setString(2, user.getId());
                        ps.setDouble(3, amountVND);
                        ps.setString(4, "Deposit via PayPal (Order ID: " + payPalOrderId + ")");
                        ps.setString(5, now);
                        ps.executeUpdate();
                    }

                    conn.commit();
                    System.out.println("[System]: \"" + YELLOW + user.getName() + RESET + "\" has deposited " + GREEN + amountVND + RESET + " VND");
                    return true;

                } catch (SQLException e) {
                    conn.rollback();
                    System.out.println("[Database]: Deposit update error: " + RED + e.getMessage() + RESET);
                    return false;
                }
            } catch (SQLException e) {
                System.out.println("[Database]: Lost connection to database: " + RED + e.getMessage() + RESET);
                return false;
            }
        };

        // Add to queue and wait for result
        try {
            return TransactionManager.submitTask(depositTask).get();
        } catch (Exception e) {
            System.out.println("[System]: Cannot process deposit order: " + e.getMessage());
            return false;
        }
    }
}