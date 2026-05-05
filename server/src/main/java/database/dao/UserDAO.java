package database.dao;

import database.DatabaseManager;
import model.user.User;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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
        String insertUserSql = "INSERT INTO users (id, username, password, name, role, is_good, totp_secret, is_totp_enabled) VALUES (?, ?, ?, ?, ?, 0, ?, 1)";
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
                return user;
            }
        }
        return null;
    }
}
