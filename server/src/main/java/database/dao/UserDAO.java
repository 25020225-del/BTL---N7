package database.dao;

import database.DatabaseManager;
import model.user.User;
import model.user.User.TwoFactorStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for user-related database operations.
 *
 * <p><b>2FA columns used:</b></p>
 * <ul>
 *   <li>{@code totp_status}      — TEXT: 'DISABLED' | 'PENDING' | 'ENABLED'</li>
 *   <li>{@code temp_totp_secret} — TEXT: provisional secret while status is PENDING</li>
 *   <li>{@code totp_secret}      — TEXT: confirmed secret, set only when ENABLED</li>
 *   <li>{@code is_totp_enabled}  — INTEGER: legacy column, kept in sync for safe rollback</li>
 * </ul>
 */
public class UserDAO {
    private final WalletDAO walletDAO = new WalletDAO();

    // ─────────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────────

    public boolean isUsernameExists(Connection conn, String userName) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userName);
            return ps.executeQuery().next();
        }
    }

    /**
     * Creates a new user and their wallet in a single transaction.
     * 2FA is opt-in: status defaults to {@code DISABLED}.
     */
    public void createUserAndWallet(Connection conn,
                                    String userId,
                                    String userName,
                                    String hashedPassword,
                                    String name,
                                    String role) throws SQLException {
        String sql = "INSERT INTO users "
                + "(id, username, password, name, role, "
                + " is_good, totp_secret, is_totp_enabled, totp_status, temp_totp_secret, is_blocked) "
                + "VALUES (?, ?, ?, ?, ?, 0, NULL, 0, 'DISABLED', NULL, 0)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, userName);
            ps.setString(3, hashedPassword);
            ps.setString(4, name);
            ps.setString(5, role.toUpperCase());
            ps.executeUpdate();
        }
        walletDAO.createWallet(conn, userId);
    }

    // ─────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────

    /**
     * Finds a user by username, including all TOTP fields (for server-side use only).
     */
    public User findUserByUsername(String userName) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapUser(rs);
            }
        }
        return null;
    }

    /**
     * Finds a user by ID, including all TOTP fields.
     */
    public User getUserById(String userId) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapUser(rs);
            }
        }
        return null;
    }

    /**
     * Returns a list of all users (public fields only — no secrets).
     *
     * <p>PATCH: Bổ sung trường {@code is_good} để Admin UI có thể hiển thị
     * trạng thái "Trusted" và bật/tắt toggle mà không cần một round-trip
     * riêng lấy chi tiết user.</p>
     */
    public List<Map<String, Object>> getAllUsers() throws SQLException {
        // CHANGED: thêm is_good vào SELECT
        String sql = "SELECT id, username, name, role, is_blocked, is_good FROM users";
        List<Map<String, Object>> users = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("id", rs.getString("id"));
                user.put("username", rs.getString("username"));
                user.put("name", rs.getString("name"));
                user.put("role", rs.getString("role"));
                user.put("is_blocked", rs.getInt("is_blocked") == 1);
                user.put("is_good", rs.getInt("is_good") == 1);   // NEW
                users.add(user);
            }
        }
        return users;
    }

    /**
     * Đảo ngược (toggle) cờ {@code is_good} của user trong một lần UPDATE
     * duy nhất, tránh race-condition read-then-write.
     *
     * <p>Sử dụng cú pháp SQLite {@code 1 - is_good} để đảo bit nguyên tử
     * ngay tại DB layer — không cần đọc giá trị hiện tại lên Java.</p>
     *
     * @param conn   Connection đang dùng (caller quản lý vòng đời).
     * @param userId ID của user cần toggle.
     * @return {@code true} nếu UPDATE thành công (row tồn tại).
     * @throws SQLException nếu có lỗi DB.
     */
    public boolean toggleGoodStatus(Connection conn, String userId) throws SQLException {
        // Atomic toggle: 1 - is_good  →  0→1 hoặc 1→0
        String sql = "UPDATE users SET is_good = 1 - is_good WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Đọc lại giá trị {@code is_good} hiện tại sau khi toggle để trả về
     * cho Client — tránh Client phải tự suy đoán trạng thái mới.
     *
     * @param conn   Connection đang dùng.
     * @param userId ID của user.
     * @return {@code true} / {@code false} tương ứng với is_good = 1 / 0,
     * hoặc {@code null} nếu user không tồn tại.
     * @throws SQLException nếu có lỗi DB.
     */
    public Boolean readGoodStatus(Connection conn, String userId) throws SQLException {
        String sql = "SELECT is_good FROM users WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("is_good") == 1;
            }
        }
        return null; // user không tồn tại
    }

    // ─────────────────────────────────────────────────────────────────
    // UPDATE — 2FA (3-State Lifecycle)
    // ─────────────────────────────────────────────────────────────────

    /**
     * <b>STEP 1 — REQUEST_2FA_SETUP</b>
     *
     * <p>Transitions status to {@code PENDING} and persists the provisional secret.
     * The confirmed {@code totp_secret} is NOT touched here.</p>
     *
     * @param userId        The user's ID.
     * @param tempSecretKey The newly generated, unverified TOTP secret.
     * @return {@code true} if the row was updated.
     */
    public boolean updateTotpPending(String userId, String tempSecretKey) throws SQLException {
        String sql = "UPDATE users SET totp_status = 'PENDING', temp_totp_secret = ?, "
                + "is_totp_enabled = 0 WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tempSecretKey);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * <b>STEP 2 — VERIFY_2FA_SETUP (success path)</b>
     *
     * <p>Promotes {@code tempSecretKey} to {@code totp_secret}, clears the temp field,
     * and sets the status to {@code ENABLED}.</p>
     *
     * @param userId          The user's ID.
     * @param confirmedSecret The same secret that was stored as {@code tempSecretKey} and just
     *                        verified successfully against the user's OTP input.
     * @return {@code true} if the row was updated.
     */
    public boolean updateTotpEnabled(String userId, String confirmedSecret) throws SQLException {
        String sql = "UPDATE users SET totp_secret = ?, temp_totp_secret = NULL, "
                + "totp_status = 'ENABLED', is_totp_enabled = 1 WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, confirmedSecret);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Resets TOÀN BỘ TOTP state về DISABLED:
     * - totp_status   = 'DISABLED'
     * - totp_secret   = NULL
     * - temp_totp_secret = NULL
     * - is_totp_enabled  = 0
     * - totp_login_enabled   = 0  ← MỚI
     * - totp_payment_enabled = 0  ← MỚI
     * <p>
     * Dùng cho: DISABLE_2FA (hủy hoàn toàn 2FA).
     */
    public boolean resetTotpToDisabled(String userId) throws SQLException {
        String sql = "UPDATE users "
                + "SET totp_status = 'DISABLED', "
                + "    temp_totp_secret = NULL, "
                + "    totp_secret = NULL, "
                + "    is_totp_enabled = 0, "
                + "    totp_login_enabled = 0, "
                + "    totp_payment_enabled = 0 "
                + "WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Resets ONLY a {@code PENDING} row back to {@code DISABLED}.
     * Unlike {@link #resetTotpToDisabled(String)}, this method uses an optimistic
     * {@code WHERE totp_status = 'PENDING'} guard so it is safe to call during
     * Login without accidentally disabling a fully-{@code ENABLED} 2FA.
     *
     * @param userId The user's ID.
     * @return {@code true} if a PENDING row was found and reset.
     */
    public boolean resetPendingTotpOnly(String userId) throws SQLException {
        String sql = "UPDATE users SET totp_status = 'DISABLED', temp_totp_secret = NULL, "
                + "is_totp_enabled = 0 WHERE id = ? AND totp_status = 'PENDING'";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            return ps.executeUpdate() > 0;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // UPDATE — legacy / other fields
    // ─────────────────────────────────────────────────────────────────

    /**
     * @deprecated Use {@link #updateTotpEnabled(String, String)} or
     * {@link #resetTotpToDisabled(String)} instead.
     * Kept here only to avoid breaking any code that still references this method
     * during the migration transition period.
     */
    @Deprecated
    public boolean updateTotpSetup(String userId, String secret, boolean enabled) throws SQLException {
        if (enabled) {
            return updateTotpEnabled(userId, secret);
        } else {
            return resetTotpToDisabled(userId);
        }
    }

    /**
     * @deprecated Use {@link #resetTotpToDisabled(String)} for DISABLE_2FA.
     * Kept for transition period.
     */
    @Deprecated
    public boolean updateTotpEnabledFlag(String userId, boolean enabled) throws SQLException {
        if (!enabled) {
            return resetTotpToDisabled(userId);
        }
        // Enabling without a secret is invalid — should not be called this way
        return false;
    }

    public boolean updateUserBlockStatus(String userId, boolean isBlocked) throws SQLException {
        String sql = "UPDATE users SET is_blocked = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, isBlocked ? 1 : 0);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateUserTrustLevel(String userId, boolean isGood) throws SQLException {
        String sql = "UPDATE users SET is_good = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, isGood ? 1 : 0);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteUser(String userId) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * <b>UPDATE_TOTP_PREFS — Cập nhật 2 cờ Login / Payment TOTP.</b>
     *
     * <p>GUARD tại DB: Chỉ cập nhật khi {@code totp_status = 'ENABLED'}.
     * Nếu user chưa thiết lập TOTP hoàn chỉnh, query sẽ không affect
     * bất kỳ row nào và method trả về {@code false}.</p>
     *
     * @param userId         ID của user.
     * @param loginEnabled   Bật/tắt yêu cầu TOTP khi đăng nhập.
     * @param paymentEnabled Bật/tắt yêu cầu TOTP khi giao dịch.
     * @return {@code true} nếu row được cập nhật thành công.
     */
    public boolean updateTotpPrefs(String userId,
                                   boolean loginEnabled,
                                   boolean paymentEnabled) throws SQLException {
        // WHERE totp_status = 'ENABLED' là guard quan trọng:
        // Ngăn việc bật cờ khi secret chưa tồn tại.
        String sql = "UPDATE users "
                + "SET totp_login_enabled = ?, totp_payment_enabled = ? "
                + "WHERE id = ? AND totp_status = 'ENABLED'";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, loginEnabled ? 1 : 0);
            ps.setInt(2, paymentEnabled ? 1 : 0);
            ps.setString(3, userId);
            return ps.executeUpdate() > 0;
        }
    }


    // ─────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────

    /**
     * Maps a {@link ResultSet} row to a {@link User} object.
     *
     * <p>The {@code totp_status} column is the authoritative source for the 2FA state.
     * If the column doesn't exist in an old DB (during a partial migration), we fall
     * back to deriving the state from {@code is_totp_enabled}.</p>
     */
    private User mapUser(ResultSet rs) throws SQLException {
        User user = new User(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getString("name"),
                rs.getString("role"));

        user.setGood(rs.getInt("is_good") == 1);
        user.setTotpSecret(rs.getString("totp_secret")); // @JsonIgnore — never sent to client

        // ── Map totp_status ──────────────────────────────────────────
        // Read the new 3-state column with graceful fallback for old schemas.
        TwoFactorStatus status;
        try {
            String statusStr = rs.getString("totp_status");
            if (statusStr == null) {
                // Column exists but value is NULL — treat as DISABLED
                status = TwoFactorStatus.DISABLED;
            } else {
                status = TwoFactorStatus.valueOf(statusStr);
            }
        } catch (IllegalArgumentException | SQLException ex) {
            // Column does not exist in this DB version (pre-migration) → derive from legacy column
            status = (rs.getInt("is_totp_enabled") == 1)
                    ? TwoFactorStatus.ENABLED
                    : TwoFactorStatus.DISABLED;
        }
        user.setTwoFactorStatus(status);

        // ── Map temp_totp_secret ─────────────────────────────────────
        try {
            user.setTempSecretKey(rs.getString("temp_totp_secret")); // @JsonIgnore
        } catch (SQLException ignored) {
            // Column not yet migrated — safe to ignore
        }
        try {
            user.setTotpLoginEnabledRaw(rs.getInt("totp_login_enabled") == 1);
        } catch (SQLException ignored) { /* cột chưa migrate — giữ false */ }
        try {
            user.setTotpPaymentEnabledRaw(rs.getInt("totp_payment_enabled") == 1);
        } catch (SQLException ignored) { /* cột chưa migrate — giữ false */ }

        // ── Handle blocked users (GIỮ NGUYÊN) ───────────────────────────
        if (rs.getInt("is_blocked") == 1) {
            user.setRole("BLOCKED");
        }

        return user;
    }
}