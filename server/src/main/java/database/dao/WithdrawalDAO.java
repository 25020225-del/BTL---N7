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
 * Data Access Object implementing persistent tracking states for multi-step
 * Maker-Checker withdrawal transactions.
 */
public class WithdrawalDAO {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_COMPLETED = "COMPLETED";

    /**
     * Stages a newly declared pending withdrawal proposal request row.
     *
     * @param conn active transaction database connection context.
     * @param requestId unique constraint tracking identifier.
     * @param userId requesting account owner identity key.
     * @param amount target numerical asset unit quantity.
     * @param payoutMethod chosen mapping distribution path gateway string.
     * @param payoutDetails metadata matching destination account info.
     * @param createdAt ISO-8601 creation chronological time record.
     * @return true if insertion script completes successfully.
     * @throws SQLException on constraint violations or platform faults.
     */
    public boolean createRequest(Connection conn, String requestId, String userId, long amount, String payoutMethod, String payoutDetails, String createdAt) throws SQLException {
        String sql = "INSERT INTO withdrawal_requests (id, user_id, amount, payout_method, payout_details, status, created_at) VALUES (?, ?, ?, ?, ?, 'PENDING', ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            ps.setString(2, userId);
            ps.setLong(3, amount);
            ps.setString(4, payoutMethod);
            ps.setString(5, payoutDetails);
            ps.setString(6, createdAt);
            return ps.executeUpdate() > 0;
        }
    }

    public Map<String, Object> getRequestById(String requestId) throws SQLException {
        String sql = "SELECT id, user_id, amount, payout_method, payout_details, status, created_at, processed_at, admin_id FROM withdrawal_requests WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public List<Map<String, Object>> getPendingRequests() throws SQLException {
        String sql = "SELECT wr.id, wr.user_id, u.username, u.name, wr.amount, wr.payout_method, wr.payout_details, wr.status, wr.created_at "
                + "FROM withdrawal_requests wr JOIN users u ON wr.user_id = u.id WHERE wr.status = 'PENDING' ORDER BY wr.created_at ASC";
        return executeListQuery(sql);
    }

    public List<Map<String, Object>> getRequestsByUser(String userId) throws SQLException {
        String sql = "SELECT id, user_id, amount, payout_method, payout_details, status, created_at, processed_at, admin_id FROM withdrawal_requests WHERE user_id = ? ORDER BY created_at DESC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            List<Map<String, Object>> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;
        }
    }

    public boolean approveWithdrawal(Connection conn, String requestId, String adminId, String processedAt) throws SQLException {
        String sql = "UPDATE withdrawal_requests SET status = 'COMPLETED', admin_id = ?, processed_at = ? WHERE id = ? AND status = 'PENDING'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, adminId);
            ps.setString(2, processedAt);
            ps.setString(3, requestId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean rejectWithdrawal(Connection conn, String requestId, String adminId, String processedAt) throws SQLException {
        String sql = "UPDATE withdrawal_requests SET status = 'REJECTED', admin_id = ?, processed_at = ? WHERE id = ? AND status = 'PENDING'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, adminId);
            ps.setString(2, processedAt);
            ps.setString(3, requestId);
            return ps.executeUpdate() > 0;
        }
    }

    public Map<String, Object> getRequestByIdWithLock(Connection conn, String requestId) throws SQLException {
        String sql = "SELECT id, user_id, amount, payout_method, payout_details, status, created_at FROM withdrawal_requests WHERE id = ? AND status = 'PENDING'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getString("id"));
        row.put("userId", rs.getString("user_id"));
        row.put("amount", rs.getLong("amount"));
        row.put("payoutMethod", rs.getString("payout_method"));
        row.put("payoutDetails", rs.getString("payout_details"));
        row.put("status", rs.getString("status"));
        row.put("createdAt", rs.getString("created_at"));

        try { row.put("processedAt", rs.getString("processed_at")); } catch (SQLException ignored) { row.put("processedAt", null); }
        try { row.put("adminId", rs.getString("admin_id")); } catch (SQLException ignored) { row.put("adminId", null); }
        try { row.put("username", rs.getString("username")); } catch (SQLException ignored) {}
        try { row.put("name", rs.getString("name")); } catch (SQLException ignored) {}

        return row;
    }

    private List<Map<String, Object>> executeListQuery(String sql) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }
}