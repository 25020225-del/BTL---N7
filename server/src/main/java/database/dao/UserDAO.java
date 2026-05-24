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
 * Data Access Object handles security credentials mapping, profile status mutations,
 * and 2FA authentication state steps for identity units.
 */
public class UserDAO {

    private final WalletDAO walletDAO = new WalletDAO();

    public boolean isUsernameExists(Connection conn, String userName) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userName);
            return ps.executeQuery().next();
        }
    }

    /**
     * Atomically registers a baseline client credentials record and initializes an associated digital wallet.
     *
     * @param conn active transaction state gateway connector.
     * @param userId unique generated structural text identity string.
     * @param userName secure alphanumeric login handle.
     * @param hashedPassword cryptographically salted security hash string.
     * @param name real identification description metadata.
     * @param role structural permission profiling descriptor.
     * @throws SQLException if a uniqueness constraint check throws an error.
     */
    public void createUserAndWallet(Connection conn, String userId, String userName, String hashedPassword, String name, String role) throws SQLException {
        String sql = "INSERT INTO users (id, username, password, name, role, is_good, totp_secret, is_totp_enabled, totp_status, temp_totp_secret, is_blocked) VALUES (?, ?, ?, ?, ?, 0, NULL, 0, 'DISABLED', NULL, 0)";
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

    public User findUserByUsername(String userName) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapUser(rs);
            }
        }
        return null;
    }

    public User getUserById(String userId) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapUser(rs);
            }
        }
        return null;
    }

    public List<Map<String, Object>> getAllUsers() throws SQLException {
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
                user.put("is_good", rs.getInt("is_good") == 1);
                users.add(user);
            }
        }
        return users;
    }

    public boolean toggleGoodStatus(Connection conn, String userId) throws SQLException {
        String sql = "UPDATE users SET is_good = 1 - is_good WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public Boolean readGoodStatus(Connection conn, String userId) throws SQLException {
        String sql = "SELECT is_good FROM users WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("is_good") == 1;
            }
        }
        return null;
    }

    public boolean updateTotpPending(String userId, String tempSecretKey) throws SQLException {
        String sql = "UPDATE users SET totp_status = 'PENDING', temp_totp_secret = ?, is_totp_enabled = 0 WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tempSecretKey);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateTotpEnabled(String userId, String confirmedSecret) throws SQLException {
        String sql = "UPDATE users SET totp_secret = ?, temp_totp_secret = NULL, totp_status = 'ENABLED', is_totp_enabled = 1 WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, confirmedSecret);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean resetTotpToDisabled(String userId) throws SQLException {
        String sql = "UPDATE users SET totp_status = 'DISABLED', temp_totp_secret = NULL, totp_secret = NULL, is_totp_enabled = 0, totp_login_enabled = 0, totp_payment_enabled = 0 WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean resetPendingTotpOnly(String userId) throws SQLException {
        String sql = "UPDATE users SET totp_status = 'DISABLED', temp_totp_secret = NULL, is_totp_enabled = 0 WHERE id = ? AND totp_status = 'PENDING'";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            return ps.executeUpdate() > 0;
        }
    }

    @Deprecated
    public boolean updateTotpSetup(String userId, String secret, boolean enabled) throws SQLException {
        return enabled ? updateTotpEnabled(userId, secret) : resetTotpToDisabled(userId);
    }

    @Deprecated
    public boolean updateTotpEnabledFlag(String userId, boolean enabled) throws SQLException {
        return !enabled && resetTotpToDisabled(userId);
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

    public boolean updateTotpPrefs(String userId, boolean loginEnabled, boolean paymentEnabled) throws SQLException {
        String sql = "UPDATE users SET totp_login_enabled = ?, totp_payment_enabled = ? WHERE id = ? AND totp_status = 'ENABLED'";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, loginEnabled ? 1 : 0);
            ps.setInt(2, paymentEnabled ? 1 : 0);
            ps.setString(3, userId);
            return ps.executeUpdate() > 0;
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        User user = new User(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getString("name"),
                rs.getString("role"));

        user.setGood(rs.getInt("is_good") == 1);
        user.setTotpSecret(rs.getString("totp_secret"));

        TwoFactorStatus status;
        try {
            String statusStr = rs.getString("totp_status");
            status = (statusStr == null) ? TwoFactorStatus.DISABLED : TwoFactorStatus.valueOf(statusStr);
        } catch (IllegalArgumentException | SQLException ex) {
            status = (rs.getInt("is_totp_enabled") == 1) ? TwoFactorStatus.ENABLED : TwoFactorStatus.DISABLED;
        }
        user.setTwoFactorStatus(status);

        try { user.setTempSecretKey(rs.getString("temp_totp_secret")); } catch (SQLException ignored) {}
        try { user.setTotpLoginEnabledRaw(rs.getInt("totp_login_enabled") == 1); } catch (SQLException ignored) {}
        try { user.setTotpPaymentEnabledRaw(rs.getInt("totp_payment_enabled") == 1); } catch (SQLException ignored) {}

        if (rs.getInt("is_blocked") == 1) {
            user.setRole("BLOCKED");
        }
        return user;
    }
}