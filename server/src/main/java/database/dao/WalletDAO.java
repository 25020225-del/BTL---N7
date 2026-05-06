package database.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

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
    public boolean updateBalance(Connection conn, String userId, double amount) throws SQLException {
        String sql = "UPDATE wallets SET balance = balance + ? WHERE user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
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
    public boolean deductBalance(Connection conn, String userId, double amount) throws SQLException {
        String sql = "UPDATE wallets SET balance = balance - ? WHERE user_id = ? AND balance >= ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setString(2, userId);
            pstmt.setDouble(3, amount);
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
    public boolean addTransaction(Connection conn, String id, String userId, double amount, String description, String createdAt) throws SQLException {
        String sql = "INSERT INTO wallet_transactions (id, user_id, amount, description, created_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, userId);
            pstmt.setDouble(3, amount);
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
}
