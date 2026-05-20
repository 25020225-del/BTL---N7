package database.dao;

import database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object (DAO) xử lý toàn bộ thao tác CRUD cho bảng
 * {@code withdrawal_requests}.
 *
 * <p><b>Mô hình Maker-Checker:</b></p>
 * <pre>
 *   User tạo yêu cầu (PENDING)
 *     → Admin duyệt  → {@link #approveWithdrawal}  → COMPLETED
 *     → Admin từ chối → {@link #rejectWithdrawal}  → REJECTED
 * </pre>
 *
 * <p><b>Lưu ý an toàn giao dịch:</b> Các phương thức nhận {@link Connection}
 * được thiết kế để chạy bên trong một transaction đã có sẵn (từ tầng Controller).
 * Các phương thức READ (không nhận Connection) tự quản lý kết nối của mình.</p>
 */
public class WithdrawalDAO {

    // ─────────────────────────────────────────────────────────────────────────
    // CONSTANTS — trạng thái yêu cầu
    // ─────────────────────────────────────────────────────────────────────────

    public static final String STATUS_PENDING   = "PENDING";
    public static final String STATUS_APPROVED  = "APPROVED";
    public static final String STATUS_REJECTED  = "REJECTED";
    public static final String STATUS_COMPLETED = "COMPLETED";

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ghi một yêu cầu rút tiền mới vào database với trạng thái {@code PENDING}.
     *
     * <p><b>⚠ QUAN TRỌNG:</b> Phương thức này phải được gọi bên trong một
     * transaction đang mở (conn.setAutoCommit(false)) cùng với các thao tác
     * trừ {@code balance} và cộng {@code locked_balance}. Điều này đảm bảo
     * tính nguyên tử (atomicity) — hoặc tất cả thành công, hoặc tất cả rollback.</p>
     *
     * @param conn          Kết nối database đang trong transaction (không được null).
     * @param requestId     ID duy nhất của yêu cầu (vd: "WD-<timestamp>-<uuid>").
     * @param userId        ID người dùng gửi yêu cầu.
     * @param amount        Số tiền muốn rút (phải > 0, tính bằng VND).
     * @param payoutMethod  Phương thức nhận tiền (vd: "BANK_TRANSFER", "MOMO").
     * @param payoutDetails Thông tin tài khoản nhận, dạng chuỗi hoặc JSON.
     * @param createdAt     Thời điểm tạo yêu cầu (ISO-8601 String).
     * @return {@code true} nếu insert thành công.
     * @throws SQLException nếu xảy ra lỗi database.
     */
    public boolean createRequest(Connection conn,
                                 String requestId,
                                 String userId,
                                 long amount,
                                 String payoutMethod,
                                 String payoutDetails,
                                 String createdAt) throws SQLException {
        String sql = "INSERT INTO withdrawal_requests "
                + "(id, user_id, amount, payout_method, payout_details, status, created_at) "
                + "VALUES (?, ?, ?, ?, ?, 'PENDING', ?)";

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

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lấy một yêu cầu rút tiền theo ID.
     *
     * @param requestId ID của yêu cầu cần tìm.
     * @return {@code Map} chứa dữ liệu yêu cầu, hoặc {@code null} nếu không tìm thấy.
     * @throws SQLException nếu xảy ra lỗi database.
     */
    public Map<String, Object> getRequestById(String requestId) throws SQLException {
        String sql = "SELECT id, user_id, amount, payout_method, payout_details, "
                + "status, created_at, processed_at, admin_id "
                + "FROM withdrawal_requests WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Lấy tất cả yêu cầu rút tiền đang ở trạng thái {@code PENDING}.
     * Dùng cho màn hình Admin.
     *
     * @return Danh sách các yêu cầu PENDING, sắp xếp theo thời gian tạo tăng dần
     *         (cũ nhất xử lý trước — FIFO).
     * @throws SQLException nếu xảy ra lỗi database.
     */
    public List<Map<String, Object>> getPendingRequests() throws SQLException {
        String sql = "SELECT wr.id, wr.user_id, u.username, u.name, wr.amount, "
                + "wr.payout_method, wr.payout_details, wr.status, wr.created_at "
                + "FROM withdrawal_requests wr "
                + "JOIN users u ON wr.user_id = u.id "
                + "WHERE wr.status = 'PENDING' "
                + "ORDER BY wr.created_at ASC";

        return executeListQuery(sql);
    }

    /**
     * Lấy toàn bộ lịch sử yêu cầu rút tiền của một người dùng cụ thể.
     *
     * @param userId ID của người dùng.
     * @return Danh sách yêu cầu, sắp xếp mới nhất lên đầu.
     * @throws SQLException nếu xảy ra lỗi database.
     */
    public List<Map<String, Object>> getRequestsByUser(String userId) throws SQLException {
        String sql = "SELECT id, user_id, amount, payout_method, payout_details, "
                + "status, created_at, processed_at, admin_id "
                + "FROM withdrawal_requests WHERE user_id = ? "
                + "ORDER BY created_at DESC";

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

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE — Admin Actions (chạy trong transaction của Controller)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Cập nhật trạng thái yêu cầu thành {@code COMPLETED} (Admin duyệt).
     *
     * <p>Dùng Optimistic Locking: chỉ update khi trạng thái hiện tại là
     * {@code PENDING} để tránh xử lý 2 lần (Double-Processing).</p>
     *
     * <p><b>⚠ QUAN TRỌNG:</b> Phải gọi bên trong transaction đang mở cùng với
     * {@link WalletDAO#deductFromLocked} để đảm bảo tính nguyên tử.</p>
     *
     * @param conn      Kết nối database đang trong transaction.
     * @param requestId ID của yêu cầu cần duyệt.
     * @param adminId   ID của Admin thực hiện duyệt.
     * @param processedAt Thời điểm xử lý (ISO-8601 String).
     * @return {@code true} nếu update thành công (row tồn tại và đang PENDING).
     * @throws SQLException nếu xảy ra lỗi database.
     */
    public boolean approveWithdrawal(Connection conn,
                                     String requestId,
                                     String adminId,
                                     String processedAt) throws SQLException {
        String sql = "UPDATE withdrawal_requests "
                + "SET status = 'COMPLETED', admin_id = ?, processed_at = ? "
                + "WHERE id = ? AND status = 'PENDING'";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, adminId);
            ps.setString(2, processedAt);
            ps.setString(3, requestId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Cập nhật trạng thái yêu cầu thành {@code REJECTED} (Admin từ chối).
     *
     * <p>Dùng Optimistic Locking: chỉ update khi trạng thái hiện tại là
     * {@code PENDING}.</p>
     *
     * <p><b>⚠ QUAN TRỌNG:</b> Phải gọi bên trong transaction đang mở cùng với
     * {@link WalletDAO#unlockBalance} để hoàn tiền về {@code balance} của user,
     * đảm bảo tính nguyên tử và không bao giờ để mất tiền.</p>
     *
     * @param conn        Kết nối database đang trong transaction.
     * @param requestId   ID của yêu cầu cần từ chối.
     * @param adminId     ID của Admin thực hiện từ chối.
     * @param processedAt Thời điểm xử lý (ISO-8601 String).
     * @return {@code true} nếu update thành công (row tồn tại và đang PENDING).
     * @throws SQLException nếu xảy ra lỗi database.
     */
    public boolean rejectWithdrawal(Connection conn,
                                    String requestId,
                                    String adminId,
                                    String processedAt) throws SQLException {
        String sql = "UPDATE withdrawal_requests "
                + "SET status = 'REJECTED', admin_id = ?, processed_at = ? "
                + "WHERE id = ? AND status = 'PENDING'";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, adminId);
            ps.setString(2, processedAt);
            ps.setString(3, requestId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Lấy dữ liệu đầy đủ của một yêu cầu trong cùng transaction đang mở.
     * Dùng khi cần đọc dữ liệu (vd: amount, user_id) trước khi quyết định
     * APPROVE hay REJECT trong cùng một transaction để tránh phantom read.
     *
     * @param conn      Kết nối database đang trong transaction.
     * @param requestId ID yêu cầu.
     * @return {@code Map} dữ liệu yêu cầu, hoặc {@code null} nếu không tồn tại.
     * @throws SQLException nếu xảy ra lỗi database.
     */
    public Map<String, Object> getRequestByIdWithLock(Connection conn,
                                                      String requestId) throws SQLException {
        // SQLite không có SELECT ... FOR UPDATE; dùng pessimistic lock thông qua
        // transaction IMMEDIATE (đã cấu hình trong HikariCP: transactionMode=IMMEDIATE)
        String sql = "SELECT id, user_id, amount, payout_method, payout_details, "
                + "status, created_at FROM withdrawal_requests "
                + "WHERE id = ? AND status = 'PENDING'";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ánh xạ một hàng {@link ResultSet} thành {@link Map} để gửi qua network.
     */
    private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id",            rs.getString("id"));
        row.put("userId",        rs.getString("user_id"));
        row.put("amount",        rs.getLong("amount"));
        row.put("payoutMethod",  rs.getString("payout_method"));
        row.put("payoutDetails", rs.getString("payout_details"));
        row.put("status",        rs.getString("status"));
        row.put("createdAt",     rs.getString("created_at"));

        // Các cột có thể NULL (chỉ có giá trị sau khi Admin xử lý)
        try { row.put("processedAt", rs.getString("processed_at")); }
        catch (SQLException ignored) { row.put("processedAt", null); }

        try { row.put("adminId", rs.getString("admin_id")); }
        catch (SQLException ignored) { row.put("adminId", null); }

        // JOIN với bảng users (chỉ có trong getPendingRequests)
        try { row.put("username", rs.getString("username")); }
        catch (SQLException ignored) { /* cột không tồn tại trong query này */ }
        try { row.put("name", rs.getString("name")); }
        catch (SQLException ignored) { /* cột không tồn tại trong query này */ }

        return row;
    }

    /**
     * Thực thi một query SELECT trả về nhiều row.
     */
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