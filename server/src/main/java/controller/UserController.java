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
 * Controller for user authentication and account security.
 *
 * <p><b>2FA — 3-State Lifecycle:</b></p>
 * <pre>
 *   setupTotp()   → status: PENDING,  tempSecretKey = newSecret  (DB written)
 *   cancelTotp()  → status: DISABLED, tempSecretKey = null        (DB written)
 *   confirmTotp() → status: ENABLED,  secretKey = tempSecretKey,
 *                            tempSecretKey = null                  (DB written)
 *   login()       → if PENDING: auto-reset → DISABLED             (DB written)
 * </pre>
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

    // ─────────────────────────────────────────────────────────────────
    // REGISTER
    // ─────────────────────────────────────────────────────────────────

    /**
     * Registers a new account. Only stores basic info; 2FA is opt-in via Settings.
     *
     * @return {@code "SUCCESS"} on success, or a user-friendly error message.
     */
    public String register(String userName, String password, String name, String role) {
        try (Connection conn = DatabaseManager.getConnection()) {
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12));
            String userId = "U-" + System.currentTimeMillis();
            userDAO.createUserAndWallet(conn, userId, userName, hashedPassword, name, role);
            return "SUCCESS";
        } catch (SQLException e) {
            boolean isConstraintViolated =
                    (e.getErrorCode() == 19)
                            || (e.getSQLState() != null && e.getSQLState().startsWith("23"));

            if (isConstraintViolated) {
                return "Tên đăng nhập đã tồn tại. Vui lòng chọn tên khác!";
            }
            log.error("Database error during registration: ", e);
            return "Lỗi hệ thống máy chủ. Vui lòng thử lại sau!";
        } catch (Exception e) {
            log.error("Unexpected error during registration: ", e);
            return "Dữ liệu đầu vào không hợp lệ!";
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // LOGIN (with PENDING auto-reset)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Authenticates the user.
     *
     * <p><b>PENDING auto-reset:</b> If the user's 2FA status is {@code PENDING}
     * (i.e. they started setup but closed the app before scanning the QR code),
     * the server resets it back to {@code DISABLED} and allows normal login.
     * This prevents a permanent lockout caused by an interrupted setup.</p>
     *
     * @param userName The login username.
     * @param password The plain-text password.
     * @return A fully populated {@link User} object on success, or {@code null} on failure.
     */
    public User login(String userName, String password) {
        try {
            User user = userDAO.findUserByUsername(userName);

            if (user != null && BCrypt.checkpw(password, user.getPassword())) {

                if ("BLOCKED".equalsIgnoreCase(user.getRole())) {
                    log.warn("Blocked user {} attempted to log in.", userName);
                    throw new AuctionExceptions.UnauthorizedAccessException(
                            "Tài khoản của bạn đã bị khóa bởi Quản trị viên.");
                }

                // ── PENDING auto-reset ─────────────────────────────────────
                // If 2FA setup was started but never finished (e.g. app was closed mid-flow),
                // reset the stale PENDING state so the user is not locked out.
                if (user.getTwoFactorStatus() == TwoFactorStatus.PENDING) {
                    log.warn(
                            "User {} logged in with PENDING 2FA status (setup was never completed). "
                                    + "Auto-resetting to DISABLED.", userName);

                    boolean reset = userDAO.resetPendingTotpOnly(user.getId());
                    if (reset) {
                        user.setTwoFactorStatus(TwoFactorStatus.DISABLED);
                        user.setTempSecretKey(null);
                        log.info("PENDING 2FA auto-reset to DISABLED for user {}.", userName);
                    } else {
                        log.error(
                                "Failed to reset PENDING 2FA for user {} in DB. "
                                        + "Proceeding with login anyway.", userName);
                        // Safety net: force the in-memory object to DISABLED so the user
                        // is not incorrectly required to enter an OTP.
                        user.setTwoFactorStatus(TwoFactorStatus.DISABLED);
                    }
                }
                // ── End PENDING auto-reset ─────────────────────────────────

                log.info("User {} ({}) logged in.", user.getName(), user.getRole());

                if (user.getRole().equalsIgnoreCase("ADMIN")) {
                    return new Admin(user.getId(), user.getUserName(),
                            user.getPassword(), user.getName());
                }
                return user;
            }

        } catch (AuctionExceptions.AuctionBaseException e) {
            throw e; // Re-throw business exceptions (e.g. UnauthorizedAccess)
        } catch (SQLException e) {
            log.error("Database error during login", e);
        }

        log.info("Login failed for username {}", userName);
        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    // 2FA — STEP 1: REQUEST_2FA_SETUP
    // ─────────────────────────────────────────────────────────────────

    /**
     * Initiates the 2FA setup flow.
     *
     * <ol>
     *   <li>Generates a fresh TOTP secret.</li>
     *   <li>Writes {@code totp_status = 'PENDING'} and {@code temp_totp_secret = newSecret}
     *       to the DB. The confirmed {@code totp_secret} is NOT changed.</li>
     *   <li>Returns the secret and QR URL for the client to display.</li>
     * </ol>
     *
     * @param userId   The authenticated user's ID.
     * @param userName The username shown in Google Authenticator.
     * @return Map with keys {@code "secretKey"} and {@code "qrUrl"}.
     * @throws SQLException if the DB update fails.
     */
    public Map<String, String> setupTotp(String userId, String userName) throws SQLException {
        String secretKey = totpService.createSecretKey();
        String qrUrl = totpService.getQRUrl(userName, secretKey);

        // Persist PENDING state — the key that triggers the lockout-prevention mechanism
        boolean saved = userDAO.updateTotpPending(userId, secretKey);
        if (!saved) {
            log.error("Failed to persist PENDING TOTP state for userId={}", userId);
            throw new SQLException("DB update failed for PENDING TOTP state.");
        }

        log.info("TOTP setup initiated for user {} (status: PENDING).", userName);

        Map<String, String> result = new HashMap<>();
        result.put("secretKey", secretKey);
        result.put("qrUrl", qrUrl);
        return result;
    }

    // ─────────────────────────────────────────────────────────────────
    // 2FA — CANCEL: CANCEL_2FA_SETUP
    // ─────────────────────────────────────────────────────────────────

    /**
     * Cancels an in-progress 2FA setup.
     *
     * <p>Resets {@code totp_status} to {@code DISABLED} and clears
     * {@code temp_totp_secret}. Safe to call even if the user is already
     * {@code DISABLED} (it is idempotent for non-{@code ENABLED} rows).</p>
     *
     * @param userId The authenticated user's ID.
     * @return {@code null} on success, or a user-friendly error message.
     */
    public String cancelTotp(String userId) {
        try {
            // resetTotpToDisabled() also clears totp_secret, but since the user never
            // confirmed (status was PENDING), totp_secret is already NULL — safe.
            // However, we only want to cancel PENDING, not accidentally wipe an ENABLED
            // user's confirmed secret. Use the targeted reset:
            userDAO.resetPendingTotpOnly(userId);
            log.info("2FA setup cancelled (PENDING → DISABLED) for userId={}.", userId);
            return null; // success

        } catch (SQLException e) {
            log.error("DB error when cancelling TOTP for userId={}", userId, e);
            return "Lỗi hệ thống khi hủy thiết lập 2FA. Vui lòng thử lại.";
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 2FA — STEP 2: VERIFY_2FA_SETUP
    // ─────────────────────────────────────────────────────────────────

    /**
     * Confirms and activates 2FA after the user enters their first OTP.
     *
     * <ol>
     *   <li>Verifies the OTP code against {@code tempSecretKey}.</li>
     *   <li>If correct: promotes {@code tempSecretKey → totp_secret},
     *       clears {@code temp_totp_secret}, sets {@code totp_status = 'ENABLED'}.</li>
     *   <li>If incorrect: returns {@code false}. The state remains {@code PENDING}.</li>
     * </ol>
     *
     * @param userId     The authenticated user's ID.
     * @param tempSecret The provisional secret stored during setup
     *                   (retrieved from {@code ClientHandler.getPendingTotpSecret()}).
     * @param code       The 6-digit OTP entered by the user.
     * @return {@code true} if verification and DB update succeeded.
     */
    public boolean confirmTotp(String userId, String tempSecret, int code) {
        try {
            if (tempSecret == null || !totpService.verifyCode(tempSecret, code)) {
                return false;
            }
            // Promote: secretKey = tempSecret, clear temp, status = ENABLED
            return userDAO.updateTotpEnabled(userId, tempSecret);

        } catch (SQLException e) {
            log.error("DB error when confirming TOTP for userId={}", userId, e);
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 2FA — DISABLE
    // ─────────────────────────────────────────────────────────────────

    /**
     * Disables 2FA after verifying via password OR current OTP.
     *
     * @param userId   The user's ID.
     * @param password The user's current password (nullable if using OTP verification).
     * @param code     The current OTP (0 if using password verification).
     * @return {@code null} on success, or a user-friendly error message.
     */
    public String disableTotp(String userId, String password, int code) {
        try {
            User user = userDAO.getUserById(userId);
            if (user == null) {
                return "Không tìm thấy tài khoản.";
            }

            boolean authenticated = false;

            if (password != null && !password.isBlank()) {
                authenticated = BCrypt.checkpw(password, user.getPassword());
            }

            if (!authenticated && code != 0 && user.getTotpSecret() != null) {
                authenticated = totpService.verifyCode(user.getTotpSecret(), code);
            }

            if (!authenticated) {
                return "Mật khẩu hoặc mã OTP không đúng.";
            }

            boolean ok = userDAO.resetTotpToDisabled(userId);
            return ok ? null : "Lỗi cơ sở dữ liệu khi tắt 2FA.";

        } catch (SQLException e) {
            log.error("DB error disabling TOTP for userId={}", userId, e);
            return "Lỗi hệ thống máy chủ. Vui lòng thử lại sau!";
        }
    }

    /**
     * Cập nhật tùy chọn TOTP granular của user.
     *
     * <p>Validation tầng service:</p>
     * <ul>
     *   <li>User phải tồn tại.</li>
     *   <li>{@code twoFactorStatus} phải là {@code ENABLED} (có secret thực).</li>
     * </ul>
     *
     * @param userId         ID của user đang đăng nhập (lấy từ session).
     * @param loginEnabled   Bật/tắt TOTP cho Đăng nhập.
     * @param paymentEnabled Bật/tắt TOTP cho Giao dịch.
     * @return {@code null} nếu thành công; chuỗi thông báo lỗi nếu thất bại.
     */
    public String updateTotpPrefs(String userId, boolean loginEnabled, boolean paymentEnabled) {
        try {
            User user = userDAO.getUserById(userId);
            if (user == null) {
                return "Không tìm thấy tài khoản.";
            }
            if (!user.is2FAEnabled()) {
                // Bảo vệ kép: server không cho phép bật cờ khi chưa setup TOTP
                return "Bạn phải thiết lập TOTP trước khi thay đổi tùy chọn này.";
            }

            boolean ok = userDAO.updateTotpPrefs(userId, loginEnabled, paymentEnabled);
            return ok ? null : "Lỗi cơ sở dữ liệu khi cập nhật tùy chọn TOTP.";

        } catch (SQLException e) {
            log.error("DB error updating TOTP prefs for userId={}", userId, e);
            return "Lỗi hệ thống. Vui lòng thử lại sau.";
        }
    }
}