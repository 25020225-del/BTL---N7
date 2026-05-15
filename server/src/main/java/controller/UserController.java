package controller;

import database.DatabaseManager;
import database.dao.UserDAO;
import model.user.Admin;
import model.user.User;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.TOTPService;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Controller responsible for managing user-related operations, including
 * authentication, account registration, and security configurations.
 */
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final TOTPService totpService;
    private final UserDAO userDAO;

    public UserController(UserDAO userDAO, TOTPService totpService) {
        this.userDAO = userDAO;
        this.totpService = totpService;
    }

    /**
     * Xử lý luồng đăng ký tài khoản mới.
     * Sử dụng cơ chế Catching SQLState/ErrorCode để chống Race Condition
     * thay vì mô hình Check-then-Act, đồng thời giữ chuẩn đầu ra cho AuthHandler.
     */
    public String register(String userName, String password, String name, String role) {
        // Sử dụng try-with-resources để tự động đóng Connection sau khi dùng xong
        try (Connection conn = DatabaseManager.getConnection()) {

            // 1. Hash mật khẩu trước khi lưu để bảo mật
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12));

            // 2. Tạo Secret Key cho hệ thống 2FA (TOTP)
            String secretKey = totpService.createSecretKey();

            // 3. Khởi tạo ID người dùng duy nhất
            String userId = "U-" + System.currentTimeMillis();

            // 4. Gọi DAO thực hiện Insert User và tạo Wallet
            // KHÔNG GỌI isUsernameExists() ở đây để triệt tiêu hoàn toàn Race Condition.
            userDAO.createUserAndWallet(conn, userId, userName, hashedPassword, name, role, secretKey);

            // 5. Trả về đúng định dạng mà AuthHandler mong đợi để build mã QR
            String qrUrl = totpService.getQRUrl(userName, secretKey);
            return "SUCCESS|" + secretKey + "|" + qrUrl;

        } catch (SQLException e) {
            // --- ARCHITECT FIX: XỬ LÝ NGOẠI LỆ AN TOÀN TRỰC TIẾP TỪ DATABASE ---
            // ErrorCode 19 là chuẩn của SQLITE_CONSTRAINT
            // SQLState bắt đầu bằng "23" là chuẩn quốc tế cho Integrity Constraint Violation
            boolean isUniqueConstraintViolated = (e.getErrorCode() == 19) ||
                    (e.getSQLState() != null && e.getSQLState().startsWith("23"));

            if (isUniqueConstraintViolated) {
                // Trả về lỗi thân thiện cho Client mà không làm sập luồng
                return "Tên đăng nhập đã tồn tại. Vui lòng chọn tên khác!";
            }

            // Ghi log lỗi hệ thống thực sự
            log.error("Database error during registration: ", e);
            return "Lỗi hệ thống máy chủ. Vui lòng thử lại sau!";
        } catch (Exception e) {
            log.error("Unexpected error during registration: ", e);
            return "Dữ liệu đầu vào không hợp lệ!";
        }
    }

    /**
     * Authenticates a user based on their username and password.
     */
    public User login(String userName, String password) {
        try {
            User user = userDAO.findUserByUsername(userName);
            if (user != null && BCrypt.checkpw(password, user.getPassword())) {
                log.info("User {} ({}) logged in.", user.getName(), user.getRole());
                if (user.getRole().equalsIgnoreCase("ADMIN")) {
                    return new Admin(user.getId(), user.getUserName(), user.getPassword(), user.getName());
                } else {
                    return user;
                }
            }
        } catch (SQLException e) {
            log.error("Database error during login", e);
        }

        log.info("Login failed for username {}", userName);
        return null;
    }
}