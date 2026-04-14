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
    // 1. TÍNH NĂNG ĐĂNG KÝ (Register with SQLite & SHA-256)
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
        String insertSql = "INSERT INTO users (id, username, password, name, role, is_good) VALUES (?, ?, ?, ?, ?, 0)";

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

            // BƯỚC NÂNG CẤP: Băm mật khẩu người dùng nhập vào
            String hashedPassword = hashPassword(password);

            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, newId);
                insertStmt.setString(2, userName);
                insertStmt.setString(3, hashedPassword); // Lưu chuỗi đã băm thay vì mật khẩu gốc
                insertStmt.setString(4, name);
                insertStmt.setString(5, role.toUpperCase());

                insertStmt.executeUpdate(); // Thực thi lưu vào ổ cứng
            }

            System.out.println("System: " + name + " Your account registration was successful. " + role);
            return "SUCCESS";

        } catch (SQLException e) {
            e.printStackTrace();
            return "Database Error: " + e.getMessage();
        }
    }

    // ==========================================
    // 2. TÍNH NĂNG ĐĂNG NHẬP (Login with SQLite & SHA-256)
    // ==========================================
    public User login(String userName, String password) {

        // Tìm người dùng trong DB có khớp tài khoản & mật khẩu không
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userName);

            // BƯỚC NÂNG CẤP: Băm mật khẩu nhập vào để so sánh với chuỗi trong DB
            String hashedPassword = hashPassword(password);
            pstmt.setString(2, hashedPassword);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // Đọc dữ liệu từ ổ cứng lên
                String id = rs.getString("id");
                String name = rs.getString("name");
                String role = rs.getString("role");

                // ĐÃ SỬA: Bỏ dòng đọc rating, chỉ giữ lại is_good
                boolean isGood = rs.getInt("is_good") == 1;

                System.out.println("System: " + name + " (" + role + ") logged in successful.");

                // Đóng gói thành đối tượng User/Admin để trả về cho hệ thống
                if (role.equalsIgnoreCase("ADMIN")) {
                    return new Admin(id, userName, password, name); // Có thể giữ nguyên password truyền vào Admin object
                } else {
                    User user = new User(id, userName, password, name, role); // Có thể giữ nguyên password truyền vào User object
                    user.setGood(isGood);
                    return user;
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error during login: " + e.getMessage());
            e.printStackTrace();
        }

        // Nếu không tìm thấy kết quả hoặc bị lỗi
        System.out.println("System: Login failed for username '" + userName + "'");
        return null;
    }
}