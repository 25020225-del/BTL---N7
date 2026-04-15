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

    // ==========================================
    // KHAI BÁO MÁY PHÁT MÃ 2FA (TOTP)
    // ==========================================
    private final service.TOTPService totpService = new service.TOTPService();

    // ==========================================
    // HÀM BẢO MẬT: BĂM MẬT KHẨU BẰNG SHA-256
    // ==========================================
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            // Chuyển mảng byte thành chuỗi Hexadecimal (hệ cơ số 16) để lưu vào DB
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
            throw new RuntimeException("Hệ thống thiếu thuật toán mã hóa SHA-256!", e);
        }
    }

    // ==========================================
    // 1. TÍNH NĂNG ĐĂNG KÝ (Đã tích hợp mã hóa và 2FA)
    // ==========================================
    public synchronized String register(String userName, String password, String name, String role) {

        // Kiểm tra vai trò hợp lệ ngay từ đầu
        if (role.equalsIgnoreCase("ADMIN")) {
            return "Error: You are not allowed to register an Admin account yourself!";
        }
        if (!role.equalsIgnoreCase("BIDDER") && !role.equalsIgnoreCase("SELLER") && !role.equalsIgnoreCase("USER")) {
            return "Error: Invalid role!";
        }

        String checkSql = "SELECT 1 FROM users WHERE username = ?";

        // ĐÃ SỬA: Bổ sung totp_secret (dấu ? thứ 6) và ép is_totp_enabled = 1
        String insertSql = "INSERT INTO users (id, username, password, name, role, is_good, totp_secret, is_totp_enabled) VALUES (?, ?, ?, ?, ?, 0, ?, 1)";

        try (Connection conn = DatabaseManager.getConnection()) {

            // 1. Kiểm tra trùng lặp Username
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, userName);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    return "Error: Username '" + userName + "' already exists";
                }
            }

            // 2. Tạo ID và Lưu vào Database SQLite
            String newId = "U-" + System.currentTimeMillis();
            String hashedPassword = hashPassword(password);

            // 3. TẠO CHÌA KHÓA BÍ MẬT 2FA
            String secretKey = totpService.createSecretKey();
            // Lấy link QR để gửi về Client
            String qrUrl = totpService.getQRUrl(userName, secretKey);

            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, newId);
                insertStmt.setString(2, userName);
                insertStmt.setString(3, hashedPassword);
                insertStmt.setString(4, name);
                insertStmt.setString(5, role.toUpperCase());

                // LƯU CHÌA KHÓA 2FA VÀO DATABASE
                insertStmt.setString(6, secretKey);

                insertStmt.executeUpdate(); // Thực thi lưu vào ổ cứng
            }

            System.out.println("System: " + name + " Your account registration was successful. 2FA Enabled.");

            // TRẢ VỀ THÀNH CÔNG KÈM THEO LINK QR ĐỂ CLIENT VẼ ẢNH
            return "SUCCESS|" + qrUrl;

        } catch (SQLException e) {
            e.printStackTrace();
            return "Database Error: " + e.getMessage();
        }
    }

    // ==========================================
    // 2. TÍNH NĂNG ĐĂNG NHẬP (Tạm thời giữ nguyên logic cũ)
    // ==========================================
    public User login(String userName, String password) {

        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userName);

            // Băm mật khẩu nhập vào để so sánh
            String hashedPassword = hashPassword(password);
            pstmt.setString(2, hashedPassword);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                String role = rs.getString("role");
                boolean isGood = rs.getInt("is_good") == 1;

                System.out.println("System: " + name + " (" + role + ") logged in successful.");

                if (role.equalsIgnoreCase("ADMIN")) {
                    return new Admin(id, userName, password, name);
                } else {
                    User user = new User(id, userName, password, name, role);
                    user.setGood(isGood);
                    return user;
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error during login: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("System: Login failed for username '" + userName + "'");
        return null;
    }
}