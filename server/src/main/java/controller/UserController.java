package controller;

import database.DatabaseManager;
import model.Admin;
import model.User;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserController {

    public static final String ANSI_RESET  = "\u001B[0m";
    public static final String ANSI_RED    = "\u001B[31m";
    public static final String ANSI_GREEN  = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE   = "\u001B[34m";

    private final service.TOTPService totpService = new service.TOTPService();

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("There's no SHA-256 algorithm", e);
        }
    }

    public synchronized String register(String userName, String password, String name, String role) {
        if (role.equalsIgnoreCase("ADMIN")) {
            return "Error: You are not allowed to register an Admin account yourself";
        }
        if (!role.equalsIgnoreCase("BIDDER") && !role.equalsIgnoreCase("SELLER") && !role.equalsIgnoreCase("USER")) {
            return "Error: Invalid role";
        }

        String checkSql  = "SELECT 1 FROM users WHERE username = ?";
        String insertSql = "INSERT INTO users (id, username, password, name, role, is_good, totp_secret, is_totp_enabled) VALUES (?, ?, ?, ?, ?, 0, ?, 1)";

        try (Connection conn = DatabaseManager.getConnection()) {
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, userName);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    return "Error: Username \"" + ANSI_YELLOW + userName + ANSI_RESET + "\" already exists";
                }
            }

            String newId          = "U-" + System.currentTimeMillis();
            String hashedPassword = hashPassword(password);
            String secretKey      = totpService.createSecretKey();
            String qrUrl          = totpService.getQRUrl(userName, secretKey);

            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, newId);
                insertStmt.setString(2, userName);
                insertStmt.setString(3, hashedPassword);
                insertStmt.setString(4, name);
                insertStmt.setString(5, role.toUpperCase());
                insertStmt.setString(6, secretKey);

                insertStmt.executeUpdate();
            }

            System.out.println("[System]: \"" + ANSI_YELLOW + userName + ANSI_RESET + "\" has just created an account. 2FA Enabled.");
            return "SUCCESS|" + secretKey + "|" + qrUrl;

        } catch (SQLException e) {
            e.printStackTrace();
            return "[Error]: Database Error: " + ANSI_RED + e.getMessage() + ANSI_RESET;
        }
    }

    public User login(String userName, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userName);
            String hashedPassword = hashPassword(password);
            pstmt.setString(2, hashedPassword);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String id      = rs.getString("id");
                String name    = rs.getString("name");
                String role    = rs.getString("role");
                boolean isGood = rs.getInt("is_good") == 1;

                System.out.println("[System]: \"" + ANSI_YELLOW + name + ANSI_RESET + "\" (" + ANSI_YELLOW + role + ANSI_RESET + ") has logged in");

                if (role.equalsIgnoreCase("ADMIN")) {
                    return new Admin(id, userName, password, name);
                } else {
                    User user = new User(id, userName, password, name, role);
                    user.setGood(isGood);
                    return user;
                }
            }
        } catch (SQLException e) {
            System.out.println("[Error]: Database error during login: " + ANSI_RED + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
        }

        System.out.println("[System]: Login failed for \"" + ANSI_YELLOW + userName + ANSI_RESET + "\"");
        return null;
    }
}