package database.dao;

import database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object (DAO) for managing wallet-related database operations.
 * Follows the MVC pattern by isolating database logic from controllers.
 */
public class WalletDAO {

    /**
     * Updates the balance of a user's wallet.
     *
     * @param conn   The active database connection (should be part of a transaction).
     * @param userId The ID of the user whose wallet is being updated.
     * @param amount The amount to add (positive) or subtract (negative).
     * @return true if the update was successful.
     * @throws SQLException if a database error occurs.
     */
    public boolean updateBalance(Connection conn, String userId, long amount) throws SQLException {
        String sql = "UPDATE wallets SET balance = balance + ? WHERE user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, amount);
            pstmt.setString(2, userId);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Deducts an amount from a user's wallet only if they have sufficient funds.
     *
     * @param conn   The active database connection.
     * @param userId The ID of the user.
     * @param amount The positive amount to deduct.
     * @return true if the deduction was successful.
     * @throws SQLException if a database error occurs.
     */
    public boolean deductBalance(Connection conn, String userId, long amount) throws SQLException {
        String sql = "UPDATE wallets SET balance = balance - ? WHERE user_id = ? AND balance >= ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, amount);
            pstmt.setString(2, userId);
            pstmt.setLong(3, amount);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Records a financial transaction in the wallet_transactions table.
     *
     * @param conn        The active database connection.
     * @param id          The unique transaction ID.
     * @param userId      The ID of the user involved.
     * @param amount      The amount of the transaction.
     * @param description A human-readable description of the transaction.
     * @param createdAt   The timestamp of the transaction.
     * @return true if the insertion was successful.
     * @throws SQLException if a database error occurs.
     */
    public boolean addTransaction(Connection conn, String id, String userId, long amount, String description, String createdAt) throws SQLException {
        String sql = "INSERT INTO wallet_transactions (id, user_id, amount, description, created_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, userId);
            pstmt.setLong(3, amount);
            pstmt.setString(4, description);
            pstmt.setString(5, createdAt);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Initializes a new wallet for a user with a zero balance.
     *
     * @param conn   The active database connection.
     * @param userId The ID of the user owning the wallet.
     * @return true if the wallet was created successfully.
     * @throws SQLException if a database error occurs.
     */
    public boolean createWallet(Connection conn, String userId) throws SQLException {
        String sql = "INSERT INTO wallets (user_id, balance) VALUES (?, 0.0)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            return pstmt.executeUpdate() > 0;
        }
    }
    /**
     * Chuyển tiền từ số dư khả dụng sang tạm giữ (Locking)
     */
    public boolean lockBalance(Connection conn, String userId, double amount) throws SQLException {
        String sql = "UPDATE wallets SET balance = balance - ?, locked_balance = locked_balance + ? " +
                "WHERE user_id = ? AND balance >= ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setDouble(2, amount);
            pstmt.setString(3, userId);
            pstmt.setDouble(4, amount);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Hoàn trả tiền từ tạm giữ về số dư khả dụng (Unlocking)
     */
    public boolean unlockBalance(Connection conn, String userId, double amount) throws SQLException {
        String sql = "UPDATE wallets SET balance = balance + ?, locked_balance = locked_balance - ? " +
                "WHERE user_id = ? AND locked_balance >= ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setDouble(2, amount);
            pstmt.setString(3, userId);
            pstmt.setDouble(4, amount);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Khấu trừ vĩnh viễn từ số tiền đã tạm giữ (Deducting from lock)
     */
    public boolean deductFromLocked(Connection conn, String userId, double amount) throws SQLException {
        String sql = "UPDATE wallets SET locked_balance = locked_balance - ? " +
                "WHERE user_id = ? AND locked_balance >= ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setString(2, userId);
            pstmt.setDouble(3, amount);
            return pstmt.executeUpdate() > 0;
        }
    }


    /**
     * Lấy giao dịch và tài khoản
     */
    public Map<String, Object> getWalletData(String userId) throws SQLException {
        String walletSql = "SELECT balance, locked_balance FROM wallets WHERE user_id = ?";
        String txnSql    = "SELECT id, amount, description, created_at FROM wallet_transactions " +
                "WHERE user_id = ? ORDER BY created_at DESC LIMIT 50";

        try (Connection conn = DatabaseManager.getConnection()) {

            Map<String, Object> result = new HashMap<>();

            // Lấy số dư
            try (PreparedStatement ps = conn.prepareStatement(walletSql)) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        result.put("balance",        rs.getLong("balance"));
                        result.put("lockedBalance",  rs.getLong("locked_balance"));
                    }
                }
            }

            // Lấy lịch sử giao dịch
            List<Map<String, Object>> transactions = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(txnSql)) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> txn = new HashMap<>();
                        txn.put("id",          rs.getString("id"));
                        txn.put("amount",      rs.getLong("amount"));
                        txn.put("description", rs.getString("description"));
                        txn.put("createdAt",   rs.getString("created_at"));
                        transactions.add(txn);
                    }
                }
            }

            result.put("transactions", transactions);
            return result;
        }
    }
}
