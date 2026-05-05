package database.dao;

import database.DatabaseManager;
import model.user.User;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserDAO {
    private final WalletDAO walletDAO = new WalletDAO();

    public boolean isUsernameExists(Connection conn, String userName) throws SQLException {
        String checkSql = "SELECT 1 FROM users WHERE username = ?";
        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, userName);
            ResultSet rs = checkStmt.executeQuery();
            return rs.next();
        }
    }

    public void createUserAndWallet(Connection conn, String userId, String userName, String hashedPassword, String name, String role, String secretKey) throws SQLException {
        String insertUserSql = "INSERT INTO users (id, username, password, name, role, is_good, totp_secret, is_totp_enabled, is_blocked) VALUES (?, ?, ?, ?, ?, 0, ?, 1, 0)";
        try (PreparedStatement insertUserStmt = conn.prepareStatement(insertUserSql)) {
            insertUserStmt.setString(1, userId);
            insertUserStmt.setString(2, userName);
            insertUserStmt.setString(3, hashedPassword);
            insertUserStmt.setString(4, name);
            insertUserStmt.setString(5, role.toUpperCase());
            insertUserStmt.setString(6, secretKey);
            insertUserStmt.executeUpdate();
        }

        walletDAO.createWallet(conn, userId);
    }

    public User findUserByUsername(String userName) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userName);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                User user = new User(
                        rs.getString("id"),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getString("name"),
                        rs.getString("role")
                );
                user.setGood(rs.getInt("is_good") == 1);
                // We should also check if the user is blocked
                if (rs.getInt("is_blocked") == 1) {
                    user.setRole("BLOCKED");
                }
                return user;
            }
        }
        return null;
    }

    public List<Map<String, Object>> getAllUsers() throws SQLException {
        String sql = "SELECT id, username, name, role, is_blocked FROM users";
        List<Map<String, Object>> users = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("id", rs.getString("id"));
                user.put("username", rs.getString("username"));
                user.put("name", rs.getString("name"));
                user.put("role", rs.getString("role"));
                user.put("is_blocked", rs.getInt("is_blocked") == 1);
                users.add(user);
            }
        }
        return users;
    }

    public boolean updateUserBlockStatus(String userId, boolean isBlocked) throws SQLException {
        String sql = "UPDATE users SET is_blocked = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, isBlocked ? 1 : 0);
            pstmt.setString(2, userId);
            return pstmt.executeUpdate() > 0;
        }
    }
}
