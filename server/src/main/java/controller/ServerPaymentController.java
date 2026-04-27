package controller;

import database.DatabaseManager;
import model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static utils.ConsoleColors.*;

public class ServerPaymentController {

    private static final java.util.concurrent.ConcurrentHashMap<String, Object> userLocks = new java.util.concurrent.ConcurrentHashMap<>();

    private Object getLockForUser(String userId) {
        return userLocks.computeIfAbsent(userId, k -> new Object());
    }

    public boolean processDepositSuccess(User user, double amountVND, String payPalOrderId) {
        Object lock = getLockForUser(user.getId());

        synchronized (lock) {
            String updateWalletSql = "UPDATE wallets SET balance = balance + ? WHERE user_id = ?";
            String insertTxnSql = "INSERT INTO wallet_transactions (id, user_id, amount, description, created_at) VALUES (?, ?, ?, ?, ?)";

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);

                try {
                    String now = LocalDateTime.now().toString();

                    try (PreparedStatement ps = conn.prepareStatement(updateWalletSql)) {
                        ps.setDouble(1, amountVND);
                        ps.setString(2, user.getId());
                        ps.executeUpdate();
                    }

                    try (PreparedStatement ps = conn.prepareStatement(insertTxnSql)) {
                        ps.setString(1, "DEP-" + System.currentTimeMillis());
                        ps.setString(2, user.getId());
                        ps.setDouble(3, amountVND);
                        ps.setString(4, "Deposit via PayPal (Transaction code: " + payPalOrderId + ")");
                        ps.setString(5, now);
                        ps.executeUpdate();
                    }

                    conn.commit();
                    System.out.println("[System]: \"" + YELLOW + user.getName() + RESET + "\" successfully deposited "
                            + amountVND + " VND");
                    return true;

                } catch (SQLException e) {
                    conn.rollback();
                    System.out.println("[Error]: Database update failed: " + RED + e.getMessage() + RESET);
                }
            } catch (SQLException e) {
                System.out.println("[Error]: Database connection lost: " + RED + e.getMessage() + RESET);
            }
        }
        return false;
    }
}