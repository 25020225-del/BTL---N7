package controller;

import database.DatabaseManager;
import model.Admin;
import model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserController {

    // ==========================================
    // 1. TÍNH NĂNG ĐĂNG KÝ (Register with SQLite)
    // ==========================================
    public synchronized String register(String userName, String password, String name, String role) {

        // Kiểm tra vai trò hợp lệ ngay từ đầu
        if (role.equalsIgnoreCase("ADMIN")) {
            return "Error: You are not allowed to register an Admin account yourself!";
        }
        if (!role.equalsIgnoreCase("BIDDER") && !role.equalsIgnoreCase("SELLER") && !role.equalsIgnoreCase("USER")) {
            return "Error: Invalid role!";
        }

        // ĐÃ SỬA: Xóa hoàn toàn rating và giá trị 5.0 khỏi câu lệnh SQL
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
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, newId);
                insertStmt.setString(2, userName);
                insertStmt.setString(3, password);
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
    // 2. TÍNH NĂNG ĐĂNG NHẬP (Login with SQLite)
    // ==========================================
    public User login(String userName, String password) {

        // Tìm người dùng trong DB có khớp tài khoản & mật khẩu không
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userName);
            pstmt.setString(2, password);

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

        // Nếu không tìm thấy kết quả hoặc bị lỗi
        System.out.println("System: Login failed for username '" + userName + "'");
        return null;
    }
}