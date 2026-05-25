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
import service.PasswordResetService;
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

    private final PasswordResetService passwordResetService;

    public UserController(UserDAO userDAO, TOTPService totpService,
                          PasswordResetService passwordResetService) {
        this.userDAO = userDAO;
        this.totpService = totpService;
        this.passwordResetService = passwordResetService;
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

    /**
     * Step 1 of the password reset flow: verify TOTP ownership.
     *
     * <p>Finds the account by username, confirms the user has TOTP enabled,
     * then validates the supplied 6-digit code against their stored secret.
     * On success, mints a single-use 5-minute reset token.</p>
     *
     * @param identifier the username of the account to reset
     * @param totpCode   the 6-digit TOTP code from the Authenticator app
     * @return a short-lived reset token, or {@code null} if verification fails
     * @throws java.sql.SQLException if a database lookup fails
     */
    public String verifyTotpForReset(String identifier, int totpCode) throws java.sql.SQLException {
        User user = userDAO.findUserByUsername(identifier);

        if (user == null) {
            log.warn("[RESET] Account not found for identifier: {}", identifier);
            return null;
        }

        if (!user.is2FAEnabled()) {
            log.warn("[RESET] Account '{}' does not have TOTP enabled — cannot use this flow.", identifier);
            return null;
        }

        String secret = user.getTotpSecret();
        if (secret == null) {
            log.error("[RESET] TOTP enabled but secret is null for userId={}", user.getId());
            return null;
        }

        if (!totpService.verifyCode(secret, totpCode)) {
            log.warn("[RESET] Invalid TOTP code for account: {}", identifier);
            return null;
        }

        String resetToken = passwordResetService.createResetToken(user.getId());
        log.info("[RESET] TOTP verified. Reset token issued for userId={}", user.getId());
        return resetToken;
    }

    /**
     * Step 2 of the password reset flow: set the new password.
     *
     * <p>Consumes the single-use reset token obtained from {@link #verifyTotpForReset},
     * applies server-side password policy checks, hashes with BCrypt, and persists.</p>
     *
     * <p><strong>Password policy enforced here:</strong>
     * <ul>
     *   <li>Minimum 8 characters</li>
     *   <li>At least one uppercase letter</li>
     *   <li>At least one digit</li>
     *   <li>At least one special character from: {@code !@#$%^&*()_+-=[]{}|;':",.<>?/}</li>
     * </ul>
     * </p>
     *
     * @param resetToken  the single-use token returned by {@link #verifyTotpForReset}
     * @param newPassword the plain-text new password (will be hashed internally)
     * @return {@code null} on success, or a Vietnamese error message string on failure
     */
    public String resetPassword(String resetToken, String newPassword) {
        String userId = passwordResetService.consumeToken(resetToken);
        if (userId == null) {
            return "Mã xác nhận không hợp lệ hoặc đã hết hạn (5 phút). Vui lòng thực hiện lại từ đầu.";
        }
        if (newPassword == null || newPassword.length() < 8) {
            return "Mật khẩu mới phải có ít nhất 8 ký tự.";
        }
        if (!newPassword.matches(".*[A-Z].*")) {
            return "Mật khẩu mới phải chứa ít nhất 1 chữ hoa (A-Z).";
        }
        if (!newPassword.matches(".*[0-9].*")) {
            return "Mật khẩu mới phải chứa ít nhất 1 chữ số (0-9).";
        }
        if (!newPassword.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;':\",./<>?].*")) {
            return "Mật khẩu mới phải chứa ít nhất 1 ký tự đặc biệt (!@#$%^&*...).";
        }
        try {
            String hashed = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
            boolean ok = userDAO.updatePassword(userId, hashed);
            if (!ok) {
                log.error("[RESET] DB update returned 0 rows for userId={}", userId);
                return "Lỗi cơ sở dữ liệu khi cập nhật mật khẩu. Vui lòng thử lại.";
            }
            log.info("[RESET] Password successfully reset for userId={}", userId);
            return null; 
        } catch (java.sql.SQLException e) {
            log.error("[RESET] SQLException updating password for userId={}: {}", userId, e.getMessage(), e);
            return "Lỗi hệ thống máy chủ. Vui lòng thử lại sau.";
        }
    }
}