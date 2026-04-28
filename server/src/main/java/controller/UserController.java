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

import static utils.ConsoleColors.*;

public class UserController {

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
            System.out.println("[Error]: Hashing algorithm not found: " + RED + e.getMessage() + RESET);
            throw new RuntimeException("There's no SHA-256 algorithm", e);
        }
    }

    public synchronized String register(String userName, String password, String name, String role) {
        if (role.equalsIgnoreCase("ADMIN")) {
            return "[Error]: You are not allowed to register an Admin account yourself";
        }
        if (!role.equalsIgnoreCase("BIDDER") && !role.equalsIgnoreCase("SELLER") && !role.equalsIgnoreCase("USER")) {
            return "[Error]: Invalid role";
        }

        String checkSql  = "SELECT 1 FROM users WHERE username = ?";
        String insertSql = "INSERT INTO users (id, username, password, name, role, is_good, totp_secret, is_totp_enabled) VALUES (?, ?, ?, ?, ?, 0, ?, 1)";
        String insertWalletSql = "INSERT INTO wallets (user_id, balance) VALUES (?, 0.0)";

        try (Connection conn = DatabaseManager.getConnection()) {
            // turn off auto commit to make sure both user and wallet are created at the same time
            conn.setAutoCommit(false);

            try {
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setString(1, userName);
                    ResultSet rs = checkStmt.executeQuery();
                    if (rs.next()) {
                        System.out.println("[System]: Registration failed, username \"" + YELLOW + userName + RESET + "\" already exists");
                        return "[Error]: Username \"" + userName + "\" already exists";
                    }
                }

                String newId = "U-" + System.currentTimeMillis();
                String hashedPassword = hashPassword(password);
                String secretKey = totpService.createSecretKey();
                String qrUrl = totpService.getQRUrl(userName, secretKey);

                try (PreparedStatement insertUserStmt = conn.prepareStatement(insertSql)) {
                    insertUserStmt.setString(1, newId);
                    insertUserStmt.setString(2, userName);
                    insertUserStmt.setString(3, hashedPassword);
                    insertUserStmt.setString(4, name);
                    insertUserStmt.setString(5, role.toUpperCase());
                    insertUserStmt.setString(6, secretKey);

                    insertUserStmt.executeUpdate();
                }

                try (PreparedStatement insertWalletStmt = conn.prepareStatement(insertWalletSql)) {
                    insertWalletStmt.setString(1, newId);
                    insertWalletStmt.executeUpdate();
                }

                System.out.println("[System]: \"" + YELLOW + userName + RESET + "\" has just created an account. 2FA Enabled.");
                return "SUCCESS|" + secretKey + "|" + qrUrl;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            System.out.println("[Error]: Database Error during registration: " + RED + e.getMessage() + RESET);
            return "[Error]: Database connection failed. Please try again later.";
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

                System.out.println("[System]: \"" + YELLOW + name + RESET + "\" (" + YELLOW + role + RESET + ") has logged in");

                if (role.equalsIgnoreCase("ADMIN")) {
                    return new Admin(id, userName, password, name);
                } else {
                    User user = new User(id, userName, password, name, role);
                    user.setGood(isGood);
                    return user;
                }
            }
        } catch (SQLException e) {
            System.out.println("[Error]: Database error during login: " + RED + e.getMessage() + RESET);
        }

        System.out.println("[System]: Login failed for \"" + YELLOW + userName + RESET + "\"");
        return null;
    }
}