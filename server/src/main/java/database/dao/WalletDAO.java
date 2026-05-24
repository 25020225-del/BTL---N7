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
 * Data Access Object managing user digital asset wallets, checking asset constraints,
 * and processing audit trail history records.
 */
public class WalletDAO {

    public boolean updateBalance(Connection conn, String userId, long amount) throws SQLException {
        String sql = "UPDATE wallets SET balance = balance + ? WHERE user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, amount);
            pstmt.setString(2, userId);
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deductBalance(Connection conn, String userId, long amount) throws SQLException {
        String sql = "UPDATE wallets SET balance = balance - ? WHERE user_id = ? AND balance >= ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, amount);
            pstmt.setString(2, userId);
            pstmt.setLong(3, amount);
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean addTransaction(Connection conn, String id, String userId, long amount, String description, String createdAt) throws SQLException {
        String sql = "INSERT INTO wallet_transactions (id, user_id, amount, description, created_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, userId);
            pstmt.setLong(3, amount);       // FIX #7: was setDouble, now setLong
            pstmt.setString(4, description);
            pstmt.setString(5, createdAt);
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean createWallet(Connection conn, String userId) throws SQLException {
        String sql = "INSERT INTO wallets (user_id, balance) VALUES (?, 0)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * FIX #7 (MEDIUM): Replaced all pstmt.setDouble() calls with pstmt.setLong() to prevent
     * floating-point precision loss on large monetary values (e.g., 999_999_999 VNĐ).
     */
    public boolean lockBalance(Connection conn, String userId, long amount) throws SQLException {
        String sql = "UPDATE wallets SET balance = balance - ?, locked_balance = locked_balance + ? WHERE user_id = ? AND balance >= ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, amount);   // FIX #7: was setDouble
            pstmt.setLong(2, amount);   // FIX #7: was setDouble
            pstmt.setString(3, userId);
            pstmt.setLong(4, amount);   // FIX #7: was setDouble
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean unlockBalance(Connection conn, String userId, long amount) throws SQLException {
        String sql = "UPDATE wallets SET balance = balance + ?, locked_balance = locked_balance - ? WHERE user_id = ? AND locked_balance >= ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, amount);   // FIX #7: was setDouble
            pstmt.setLong(2, amount);   // FIX #7: was setDouble
            pstmt.setString(3, userId);
            pstmt.setLong(4, amount);   // FIX #7: was setDouble
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deductFromLocked(Connection conn, String userId, long amount) throws SQLException {
        String sql = "UPDATE wallets SET locked_balance = locked_balance - ? WHERE user_id = ? AND locked_balance >= ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, amount);   // FIX #7: was setDouble
            pstmt.setString(2, userId);
            pstmt.setLong(3, amount);   // FIX #7: was setDouble
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Aggregates active ledger details and chronological statement history listings.
     *
     * @param userId target owner profile identity pointer.
     * @return payload containing mapped accounting states and ledger elements.
     * @throws SQLException if structural queries drop.
     */
    public Map<String, Object> getWalletData(String userId) throws SQLException {
        String walletSql = "SELECT balance, locked_balance FROM wallets WHERE user_id = ?";
        String txnSql = "SELECT id, amount, description, created_at FROM wallet_transactions WHERE user_id = ? ORDER BY created_at DESC LIMIT 50";

        try (Connection conn = DatabaseManager.getConnection()) {
            Map<String, Object> result = new HashMap<>();

            try (PreparedStatement ps = conn.prepareStatement(walletSql)) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        result.put("balance", rs.getLong("balance"));
                        result.put("lockedBalance", rs.getLong("locked_balance"));
                    }
                }
            }

            List<Map<String, Object>> transactions = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(txnSql)) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> txn = new HashMap<>();
                        txn.put("id", rs.getString("id"));
                        txn.put("amount", rs.getLong("amount"));
                        txn.put("description", rs.getString("description"));
                        txn.put("createdAt", rs.getString("created_at"));
                        transactions.add(txn);
                    }
                }
            }
            result.put("transactions", transactions);
            return result;
        }
    }
}