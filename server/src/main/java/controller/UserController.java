package controller;

import database.DatabaseManager;
import database.dao.UserDAO;
import exception.AuctionExceptions;
import model.user.Admin;
import model.user.User;
import model.user.User.TwoFactorStatus;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.TOTPService;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller executing identity validation contexts, cryptographic security enforcement pipelines,
 * and state modifications for two-factor authentication (2FA/TOTP) credentials.
 */
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final TOTPService totpService;
    private final UserDAO userDAO;

    public UserController(UserDAO userDAO, TOTPService totpService) {
        this.userDAO = userDAO;
        this.totpService = totpService;
    }

    public TOTPService getTotpService() {
        return totpService;
    }

    public String register(String userName, String password, String name, String role) {
        try (Connection conn = DatabaseManager.getConnection()) {
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12));
            String userId = utils.IdGenerator.generateUUIDv7();
            userDAO.createUserAndWallet(conn, userId, userName, hashedPassword, name, role);
            return "SUCCESS";
        } catch (SQLException e) {
            boolean isConstraintViolated = (e.getErrorCode() == 19) || (e.getSQLState() != null && e.getSQLState().startsWith("23"));
            if (isConstraintViolated) {
                return "Tên đăng nhập đã tồn tại. Vui lòng chọn tên khác!";
            }
            return "Lỗi hệ thống máy chủ. Vui lòng thử lại sau!";
        } catch (Exception e) {
            return "Dữ liệu đầu vào không hợp lệ!";
        }
    }

    public User login(String userName, String password) {
        try {
            User user = userDAO.findUserByUsername(userName);
            if (user != null && BCrypt.checkpw(password, user.getPassword())) {

                if ("BLOCKED".equalsIgnoreCase(user.getRole())) {
                    throw new AuctionExceptions.UnauthorizedAccessException("Tài khoản của bạn đã bị khóa bởi Quản trị viên.");
                }

                if (user.getTwoFactorStatus() == TwoFactorStatus.PENDING) {
                    if (userDAO.resetPendingTotpOnly(user.getId())) {
                        user.setTwoFactorStatus(TwoFactorStatus.DISABLED);
                        user.setTempSecretKey(null);
                    } else {
                        user.setTwoFactorStatus(TwoFactorStatus.DISABLED);
                    }
                }

                log.info("User {} ({}) logged in.", user.getName(), user.getRole());
                return user.getRole().equalsIgnoreCase("ADMIN")
                        ? new Admin(user.getId(), user.getUserName(), user.getPassword(), user.getName()) : user;
            }
        } catch (AuctionExceptions.AuctionBaseException e) {
            throw e;
        } catch (SQLException e) {
            log.error("Database error during login", e);
        }
        return null;
    }

    public Map<String, String> setupTotp(String userId, String userName) throws SQLException {
        String secretKey = totpService.createSecretKey();
        String qrUrl = totpService.getQRUrl(userName, secretKey);

        if (!userDAO.updateTotpPending(userId, secretKey)) {
            throw new SQLException("DB update failed for PENDING TOTP state.");
        }

        Map<String, String> result = new HashMap<>();
        result.put("secretKey", secretKey);
        result.put("qrUrl", qrUrl);
        return result;
    }

    public String cancelTotp(String userId) {
        try {
            userDAO.resetPendingTotpOnly(userId);
            return null;
        } catch (SQLException e) {
            log.error("DB error when cancelling TOTP for userId={}", userId, e);
            return "Lỗi hệ thống khi hủy thiết lập 2FA. Vui lòng thử lại.";
        }
    }

    public boolean confirmTotp(String userId, String tempSecret, int code) {
        try {
            if (tempSecret == null || !totpService.verifyCode(tempSecret, code)) {
                return false;
            }
            return userDAO.updateTotpEnabled(userId, tempSecret);
        } catch (SQLException e) {
            log.error("DB error when confirming TOTP for userId={}", userId, e);
            return false;
        }
    }

    public String disableTotp(String userId, String password, int code) {
        try {
            User user = userDAO.getUserById(userId);
            if (user == null) return "Không tìm thấy tài khoản.";

            boolean authenticated = false;
            if (password != null && !password.isBlank()) {
                authenticated = BCrypt.checkpw(password, user.getPassword());
            }
            if (!authenticated && code != 0 && user.getTotpSecret() != null) {
                authenticated = totpService.verifyCode(user.getTotpSecret(), code);
            }
            if (!authenticated) return "Mật khẩu hoặc mã OTP không đúng.";

            return userDAO.resetTotpToDisabled(userId) ? null : "Lỗi cơ sở dữ liệu khi tắt 2FA.";
        } catch (SQLException e) {
            log.error("DB error disabling TOTP for userId={}", userId, e);
            return "Lỗi hệ thống máy chủ. Vui lòng thử lại sau!";
        }
    }

    public String updateTotpPrefs(String userId, boolean loginEnabled, boolean paymentEnabled) {
        try {
            User user = userDAO.getUserById(userId);
            if (user == null) return "Không tìm thấy tài khoản.";
            if (!user.is2FAEnabled()) return "Bạn phải thiết lập TOTP trước khi thay đổi tùy chọn này.";

            return userDAO.updateTotpPrefs(userId, loginEnabled, paymentEnabled) ? null : "Lỗi cơ sở dữ liệu khi cập nhật tùy chọn TOTP.";
        } catch (SQLException e) {
            log.error("DB error updating TOTP prefs for userId={}", userId, e);
            return "Lỗi hệ thống. Vui lòng thử lại sau.";
        }
    }
}