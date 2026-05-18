package server.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import exception.AuctionExceptions;
import model.user.Admin;
import model.user.User;
import network.ErrorPayload;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ClientHandler;
import utils.JacksonConfig;

import java.util.Map;

/**
 * Xử lý tất cả command liên quan đến xác thực và quản lý phiên.
 *
 * <p>Danh sách lệnh:</p>
 * <pre>
 * LOGIN              → kiểm tra mật khẩu; nếu isTotpLoginEnabled=true → REQUIRE_2FA
 * VERIFY_2FA         → kiểm tra OTP khi đăng nhập; nếu OK → LOGIN_SUCCESS
 * REGISTER           → tạo tài khoản
 * LOGOUT             → xóa session
 * REQUEST_SETUP_2FA  → tạo secret tạm, set PENDING trong DB, trả về QR
 * CANCEL_2FA_SETUP   → reset PENDING → DISABLED
 * VERIFY_2FA_SETUP   → xác nhận OTP; nếu OK → ENABLED
 * DISABLE_2FA        → HỦY HOÀN TOÀN 2FA: xóa secret, reset 2 cờ, set DISABLED
 * UPDATE_TOTP_PREFS  → cập nhật cờ isTotpLoginEnabled / isTotpPaymentEnabled (NEW)
 * </pre>
 */
public class AuthHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthHandler.class);
    private final ObjectMapper mapper = JacksonConfig.mapper();

    @Override
    public void handle(NetworkMessage message, ClientHandler client) throws Exception {
        switch (message.getCommand()) {
            case "LOGIN"              -> processLogin(message.getData(), client);
            case "VERIFY_2FA"         -> processVerify2FA(message.getData(), client);
            case "REGISTER"           -> processRegister(message.getData(), client);
            case "LOGOUT"             -> processLogout(client);
            case "REQUEST_SETUP_2FA"  -> processRequestSetup2FA(client);
            case "CANCEL_2FA_SETUP"   -> processCancelSetup2FA(client);   // ← NEW
            case "VERIFY_2FA_SETUP"   -> processVerify2FASetup(message.getData(), client); // ← RENAMED
            case "CONFIRM_SETUP_2FA"  -> processVerify2FASetup(message.getData(), client); // ← backward-compat alias
            case "DISABLE_2FA"        -> processDisable2FA(message.getData(), client);
            default -> throw new AuctionExceptions.InvalidPayloadException(
                    "Lệnh xác thực không hợp lệ: " + message.getCommand());
        }
    }

    // ── LOGIN ────────────────────────────────────────────────────────

    private void processLogin(Object data, ClientHandler client) throws Exception {
        User loginAttempt;
        try {
            loginAttempt = mapper.convertValue(data, User.class);
        } catch (IllegalArgumentException e) {
            throw new AuctionExceptions.InvalidPayloadException("Dữ liệu đăng nhập không đúng định dạng.");
        }

        User user = client.getUserController()
                .login(loginAttempt.getUserName(), loginAttempt.getPassword());

        if (user == null) {
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_AUTH_003", "Sai tên đăng nhập hoặc mật khẩu."));
            return;
        }

        if (user.isTotpLoginEnabled()) {
            client.setPendingUser(user);
            client.sendResponse("REQUIRE_2FA",
                    Map.of("message", "Vui lòng nhập mã xác thực 2FA (6 số)."));
            log.info("User {} requires TOTP at login.", user.getUserName());
        } else {
            finalizeLogin(user, client);
        }
    }

    // ── VERIFY_2FA (login step) ───────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void processVerify2FA(Object data, ClientHandler client) {
        User pending = client.getPendingUser();
        if (pending == null) {
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_AUTH_010",
                            "Không có phiên xác thực 2FA nào đang chờ."));
            return;
        }

        int code;
        try {
            Map<String, Object> map = (Map<String, Object>) data;
            code = ((Number) map.get("code")).intValue();
        } catch (Exception e) {
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_AUTH_011",
                            "Mã OTP không đúng định dạng."));
            return;
        }

        String secret   = pending.getTotpSecret();
        boolean verified = (secret != null)
                && client.getUserController()
                .getTotpService()
                .verifyCode(secret, code);

        if (verified) {
            client.setPendingUser(null);
            finalizeLogin(pending, client);
        } else {
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_AUTH_012",
                            "Mã OTP không hợp lệ hoặc đã hết hạn."));
        }
    }

    private void finalizeLogin(User user, ClientHandler client) {
        client.setClientName(user.getUserName());
        client.setUser(user);
        // totpSecret is @JsonIgnore — never appears in the payload
        client.sendResponse("LOGIN_SUCCESS", user);
        log.info("{} ({}) has logged in.", user.getUserName(), user.getRole());
    }

    // ── REGISTER ─────────────────────────────────────────────────────

    private void processRegister(Object data, ClientHandler client) throws Exception {
        User regUser;
        try {
            regUser = mapper.convertValue(data, User.class);
        } catch (IllegalArgumentException e) {
            throw new AuctionExceptions.InvalidPayloadException(
                    "Dữ liệu đăng ký không đúng định dạng.");
        }

        String result = client.getUserController().register(
                regUser.getUserName(),
                regUser.getPassword(),
                regUser.getName(),
                regUser.getRole());

        if ("SUCCESS".equals(result)) {
            client.sendResponse("REGISTER_SUCCESS",
                    Map.of("message", "Đăng ký thành công! Hãy đăng nhập."));
            client.setClientName(regUser.getUserName());
            log.info("New account registered: {}", regUser.getUserName());
        } else {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_005", result));
        }
    }

    // ── LOGOUT ───────────────────────────────────────────────────────

    private void processLogout(ClientHandler client) {
        String oldName = client.getClientName();
        client.setUser(null);
        client.setPendingUser(null);
        client.setPendingTotpSecret(null);
        client.setClientName("#Guest" + ClientHandler.getcNC());
        ClientHandler.incrementcNC();
        log.info("{} signed out.", oldName);
    }

    // ── REQUEST_SETUP_2FA ────────────────────────────────────────────
    //   STEP 1 of the setup flow

    private void processRequestSetup2FA(ClientHandler client) {
        User currentUser = client.getUser();
        if (currentUser == null) {
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_AUTH_020", "Bạn phải đăng nhập trước."));
            return;
        }

        try {
            // setupTotp() now writes PENDING + tempSecretKey to the DB
            Map<String, String> totpData = client.getUserController()
                    .setupTotp(currentUser.getId(), currentUser.getUserName());

            // Also cache in session RAM for efficiency (avoids a DB roundtrip on VERIFY)
            client.setPendingTotpSecret(totpData.get("secretKey"));

            // Update the in-memory user object to reflect PENDING status
            currentUser.setTwoFactorStatus(User.TwoFactorStatus.PENDING);

            client.sendResponse("SETUP_2FA_SUCCESS", totpData);
            log.info("TOTP setup initiated (PENDING) for user: {}.", currentUser.getUserName());

        } catch (Exception e) {
            log.error("Error setting up TOTP for user {}: {}", currentUser.getUserName(), e.getMessage());
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_AUTH_021",
                            "Lỗi máy chủ khi tạo mã QR. Vui lòng thử lại."));
        }
    }

    // ── CANCEL_2FA_SETUP ─────────────────────────────────────────────
    //   Called when the user closes/cancels the QR dialog without confirming

    private void processCancelSetup2FA(ClientHandler client) {
        User currentUser = client.getUser();
        if (currentUser == null) {
            // User is not authenticated — nothing to cancel
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_AUTH_020", "Bạn phải đăng nhập trước."));
            return;
        }

        // Only cancel if we are actually in PENDING state — guard against double-calls
        if (currentUser.getTwoFactorStatus() != User.TwoFactorStatus.PENDING) {
            // Already DISABLED or ENABLED — nothing to do; respond OK to unblock the UI
            client.sendResponse("CANCEL_2FA_SUCCESS",
                    Map.of("message", "Không có thiết lập 2FA nào đang chờ để hủy."));
            return;
        }

        String error = client.getUserController().cancelTotp(currentUser.getId());

        if (error == null) {
            // Clear session state
            client.setPendingTotpSecret(null);
            currentUser.setTwoFactorStatus(User.TwoFactorStatus.DISABLED);
            currentUser.setTempSecretKey(null);

            client.sendResponse("CANCEL_2FA_SUCCESS",
                    Map.of("message", "Đã hủy thiết lập 2FA."));
            log.info("2FA setup cancelled for user {}.", currentUser.getUserName());
        } else {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_028", error));
        }
    }

    // ── VERIFY_2FA_SETUP ─────────────────────────────────────────────
    //   STEP 2: user enters their first OTP to confirm setup

    @SuppressWarnings("unchecked")
    private void processVerify2FASetup(Object data, ClientHandler client) {
        User currentUser       = client.getUser();
        String pendingSecret   = client.getPendingTotpSecret();

        if (currentUser == null || pendingSecret == null) {
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_AUTH_022",
                            "Không có phiên thiết lập 2FA nào đang hoạt động."));
            return;
        }

        int code;
        try {
            Map<String, Object> map = (Map<String, Object>) data;
            code = ((Number) map.get("code")).intValue();
        } catch (Exception e) {
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_AUTH_023",
                            "Mã OTP không đúng định dạng."));
            return;
        }

        boolean ok = client.getUserController()
                .confirmTotp(currentUser.getId(), pendingSecret, code);

        if (ok) {
            // Clear temp state from session
            client.setPendingTotpSecret(null);

            // Promote in-memory user object: tempSecretKey → no longer needed,
            // status → ENABLED (DB already updated by confirmTotp)
            currentUser.setTwoFactorStatus(User.TwoFactorStatus.ENABLED);
            currentUser.setTempSecretKey(null);

            client.sendResponse("CONFIRM_2FA_SUCCESS",
                    Map.of("message",
                            "Xác thực 2 lớp đã được bật thành công! "
                                    + "Lần đăng nhập tiếp theo sẽ yêu cầu mã OTP."));
            log.info("2FA successfully ENABLED for user {}.", currentUser.getUserName());

        } else {
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_AUTH_024",
                            "Mã OTP không hợp lệ hoặc đã hết hạn. Vui lòng thử lại."));
        }
    }

    // ── DISABLE_2FA ───────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void processDisable2FA(Object data, ClientHandler client) {
        User currentUser = client.getUser();
        if (currentUser == null) {
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_AUTH_020", "Bạn phải đăng nhập trước."));
            return;
        }

        String password = null;
        int    code     = 0;
        try {
            Map<String, Object> map = (Map<String, Object>) data;
            if (map.containsKey("password")) {
                password = (String) map.get("password");
            }
            if (map.containsKey("code") && map.get("code") != null) {
                code = ((Number) map.get("code")).intValue();
            }
        } catch (Exception e) {
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_AUTH_025", "Dữ liệu xác thực không đúng định dạng."));
            return;
        }

        // disableTotp() gọi resetTotpToDisabled() → đã được cập nhật để reset cả 2 cờ mới
        String error = client.getUserController()
                .disableTotp(currentUser.getId(), password, code);

        if (error == null) {
            // ── Cập nhật in-memory user object ──────────────────────────
            // setTwoFactorStatus(DISABLED) tự động reset 2 cờ nhờ logic trong User.java
            currentUser.setTwoFactorStatus(User.TwoFactorStatus.DISABLED);
            currentUser.setTotpSecret(null);
            // Hai cờ đã được reset bởi setTwoFactorStatus() ở trên ↑

            client.sendResponse("DISABLE_2FA_SUCCESS",
                    Map.of("message",
                            "Đã hủy hoàn toàn xác thực 2 lớp. "
                                    + "Tài khoản của bạn hiện chỉ được bảo vệ bằng mật khẩu."));
            log.info("2FA fully disabled for user {}.", currentUser.getUserName());
        } else {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_026", error));
        }
    }
    // ── UPDATE_TOTP_PREFS ─────────────────────────────

    /**
     * Xử lý lệnh {@code UPDATE_TOTP_PREFS}.
     *
     * <p>Client gửi payload: {@code Map<String, Boolean>} với keys
     * {@code "loginEnabled"} và {@code "paymentEnabled"}.</p>
     *
     * <p>Flow:</p>
     * <ol>
     *   <li>Kiểm tra authentication.</li>
     *   <li>Validate payload.</li>
     *   <li>Gọi {@code UserController.updateTotpPrefs()} → kiểm tra ENABLED state tại service layer.</li>
     *   <li>Cập nhật in-memory user object.</li>
     *   <li>Trả về {@code UPDATE_TOTP_PREFS_SUCCESS} kèm trạng thái mới.</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    private void processUpdateTotpPrefs(Object data, ClientHandler client) {
        User currentUser = client.getUser();
        if (currentUser == null) {
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_AUTH_030", "Bạn phải đăng nhập trước."));
            return;
        }

        boolean loginEnabled;
        boolean paymentEnabled;
        try {
            Map<String, Object> map = (Map<String, Object>) data;
            loginEnabled   = Boolean.TRUE.equals(map.get("loginEnabled"));
            paymentEnabled = Boolean.TRUE.equals(map.get("paymentEnabled"));
        } catch (Exception e) {
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_AUTH_031", "Payload không đúng định dạng."));
            return;
        }

        String error = client.getUserController()
                .updateTotpPrefs(currentUser.getId(), loginEnabled, paymentEnabled);

        if (error == null) {
            // Cập nhật in-memory user object (raw setter — đây là server, secret đã tồn tại)
            currentUser.setTotpLoginEnabledRaw(loginEnabled);
            currentUser.setTotpPaymentEnabledRaw(paymentEnabled);

            client.sendResponse("UPDATE_TOTP_PREFS_SUCCESS",
                    Map.of(
                            "loginEnabled",   loginEnabled,
                            "paymentEnabled", paymentEnabled,
                            "message",        "Tùy chọn TOTP đã được cập nhật."
                    ));
            log.info("TOTP prefs updated for user {}: login={}, payment={}",
                    currentUser.getUserName(), loginEnabled, paymentEnabled);
        } else {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_032", error));
        }
    }
}