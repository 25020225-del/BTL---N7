package server.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import exception.AuctionExceptions;
import model.user.User;
import network.ErrorPayload;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ClientHandler;
import utils.JacksonConfig;

import java.util.Map;

/**
 * Network ingress command route handler managing user authentication,
 * session invalidation, and secure two-factor identity verification states.
 */
public class AuthHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthHandler.class);
    private final ObjectMapper mapper = JacksonConfig.mapper();

    @Override
    public void handle(NetworkMessage message, ClientHandler client) throws Exception {
        switch (message.getCommand()) {
            case "LOGIN" -> processLogin(message.getData(), client);
            case "VERIFY_2FA" -> processVerify2FA(message.getData(), client);
            case "REGISTER" -> processRegister(message.getData(), client);
            case "LOGOUT" -> processLogout(client);
            case "REQUEST_SETUP_2FA" -> processRequestSetup2FA(client);
            case "CANCEL_2FA_SETUP" -> processCancelSetup2FA(client);
            case "VERIFY_2FA_SETUP", "CONFIRM_SETUP_2FA" -> processVerify2FASetup(message.getData(), client);
            case "DISABLE_2FA" -> processDisable2FA(message.getData(), client);
            case "UPDATE_TOTP_PREFS" -> processUpdateTotpPrefs(message.getData(), client);
            default -> throw new AuctionExceptions.InvalidPayloadException("Lệnh xác thực không hợp lệ: " + message.getCommand());
        }
    }

    private void processLogin(Object data, ClientHandler client) throws Exception {
        log.info("[LOGIN] Raw data received: {}", data);
        User loginAttempt;
        try {
            loginAttempt = mapper.convertValue(data, User.class);
            log.info("[LOGIN] Parsed username: {}", loginAttempt.getUserName());
        } catch (IllegalArgumentException e) {
            log.error("[LOGIN] Jackson deserialization FAILED: {}", e.getMessage());
            throw new AuctionExceptions.InvalidPayloadException("Dữ liệu đăng nhập không hợp lệ.");
        }

        User user = client.getUserController().login(loginAttempt.getUserName(), loginAttempt.getPassword());
        if (user == null) {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_003", "Sai tên đăng nhập hoặc mật khẩu."));
            return;
        }

        if (user.isTotpLoginEnabled()) {
            client.setPendingUser(user);
            client.sendResponse("REQUIRE_2FA", Map.of("message", "Vui lòng nhập mã xác thực 2FA (6 số)."));
            log.info("User {} requires TOTP at login.", user.getUserName());
        } else {
            finalizeLogin(user, client);
        }
        log.info("[LOGIN] DB lookup result: user={}, totpEnabled={}",
                user != null ? user.getUserName() : "null",
                user != null && user.isTotpLoginEnabled());
    }

    private void processVerify2FA(Object data, ClientHandler client) {
        User pending = client.getPendingUser();
        if (pending == null) {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_010", "Không có phiên xác thực 2FA nào đang chờ."));
            return;
        }

        int code;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) data;
            code = ((Number) map.get("code")).intValue();
        } catch (Exception e) {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_011", "Mã OTP không đúng định dạng."));
            return;
        }

        String secret = pending.getTotpSecret();
        boolean verified = (secret != null) && client.getUserController().getTotpService().verifyCode(secret, code);

        if (verified) {
            client.setPendingUser(null);
            finalizeLogin(pending, client);
        } else {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_012", "Mã OTP không hợp lệ hoặc đã hết hạn."));
        }
    }

    private void finalizeLogin(User user, ClientHandler client) {
        client.setClientName(user.getUserName());
        client.setUser(user);
        client.sendResponse("LOGIN_SUCCESS", user);
        log.info("{} ({}) has logged in.", user.getUserName(), user.getRole());
    }

    private void processRegister(Object data, ClientHandler client) throws Exception {
        User regUser;
        try {
            regUser = mapper.convertValue(data, User.class);
        } catch (IllegalArgumentException e) {
            throw new AuctionExceptions.InvalidPayloadException("Dữ liệu đăng ký không đúng định dạng.");
        }

        String result = client.getUserController().register(
                regUser.getUserName(),
                regUser.getPassword(),
                regUser.getName(),
                regUser.getRole());

        if ("SUCCESS".equals(result)) {
            client.sendResponse("REGISTER_SUCCESS", Map.of("message", "Đăng ký thành công! Hãy đăng nhập."));
            client.setClientName(regUser.getUserName());
            log.info("New account registered: {}", regUser.getUserName());
        } else {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_005", result));
        }
    }

    private void processLogout(ClientHandler client) {
        String oldName = client.getClientName();
        client.setUser(null);
        client.setPendingUser(null);
        client.setPendingTotpSecret(null);
        client.setClientName("#Guest" + ClientHandler.nextClientNumber());
        log.info("{} signed out.", oldName);
    }

    private void processRequestSetup2FA(ClientHandler client) {
        User currentUser = client.getUser();
        if (currentUser == null) {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_020", "Bạn phải đăng nhập trước."));
            return;
        }

        try {
            Map<String, String> totpData = client.getUserController().setupTotp(currentUser.getId(), currentUser.getUserName());
            client.setPendingTotpSecret(totpData.get("secretKey"));
            currentUser.setTwoFactorStatus(User.TwoFactorStatus.PENDING);
            client.sendResponse("SETUP_2FA_SUCCESS", totpData);
            log.info("TOTP setup initiated (PENDING) for user: {}.", currentUser.getUserName());
        } catch (Exception e) {
            log.error("Error setting up TOTP for user {}: {}", currentUser.getUserName(), e.getMessage());
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_021", "Lỗi máy chủ khi tạo mã QR. Vui lòng thử lại."));
        }
    }

    private void processCancelSetup2FA(ClientHandler client) {
        User currentUser = client.getUser();
        if (currentUser == null) {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_020", "Bạn phải đăng nhập trước."));
            return;
        }

        if (currentUser.getTwoFactorStatus() != User.TwoFactorStatus.PENDING) {
            client.sendResponse("CANCEL_2FA_SUCCESS", Map.of("message", "Không có thiết lập 2FA nào đang chờ để hủy."));
            return;
        }

        String error = client.getUserController().cancelTotp(currentUser.getId());
        if (error == null) {
            client.setPendingTotpSecret(null);
            currentUser.setTwoFactorStatus(User.TwoFactorStatus.DISABLED);
            currentUser.setTempSecretKey(null);
            client.sendResponse("CANCEL_2FA_SUCCESS", Map.of("message", "Đã hủy thiết lập 2FA."));
            log.info("2FA setup cancelled for user {}.", currentUser.getUserName());
        } else {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_028", error));
        }
    }

    private void processVerify2FASetup(Object data, ClientHandler client) {
        User currentUser = client.getUser();
        String pendingSecret = client.getPendingTotpSecret();

        if (currentUser == null || pendingSecret == null) {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_022", "Không có phiên thiết lập 2FA nào đang hoạt động."));
            return;
        }

        int code;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) data;
            code = ((Number) map.get("code")).intValue();
        } catch (Exception e) {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_023", "Mã OTP không đúng định dạng."));
            return;
        }

        boolean ok = client.getUserController().confirmTotp(currentUser.getId(), pendingSecret, code);
        if (ok) {
            client.setPendingTotpSecret(null);
            currentUser.setTwoFactorStatus(User.TwoFactorStatus.ENABLED);
            currentUser.setTempSecretKey(null);
            client.sendResponse("CONFIRM_2FA_SUCCESS", Map.of("message", "Xác thực 2 lớp đã được bật thành công! Lần đăng nhập tiếp theo sẽ yêu cầu mã OTP."));
            log.info("2FA successfully ENABLED for user {}.", currentUser.getUserName());
        } else {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_024", "Mã OTP không hợp lệ hoặc đã hết hạn. Vui lòng thử lại."));
        }
    }

    private void processDisable2FA(Object data, ClientHandler client) {
        User currentUser = client.getUser();
        if (currentUser == null) {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_020", "Bạn phải đăng nhập trước."));
            return;
        }

        String password = null;
        int code = 0;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) data;
            if (map.containsKey("password")) {
                password = (String) map.get("password");
            }
            if (map.containsKey("code") && map.get("code") != null) {
                code = ((Number) map.get("code")).intValue();
            }
        } catch (Exception e) {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_025", "Dữ liệu xác thực không đúng định dạng."));
            return;
        }

        String error = client.getUserController().disableTotp(currentUser.getId(), password, code);
        if (error == null) {
            currentUser.setTwoFactorStatus(User.TwoFactorStatus.DISABLED);
            currentUser.setTotpSecret(null);
            client.sendResponse("DISABLE_2FA_SUCCESS", Map.of("message", "Đã hủy hoàn toàn xác thực 2 lớp. Tài khoản của bạn hiện chỉ được bảo vệ bằng mật khẩu."));
            log.info("2FA fully disabled for user {}.", currentUser.getUserName());
        } else {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_026", error));
        }
    }

    private void processUpdateTotpPrefs(Object data, ClientHandler client) {
        User currentUser = client.getUser();
        if (currentUser == null) {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_030", "Bạn phải đăng nhập trước."));
            return;
        }

        boolean loginEnabled;
        boolean paymentEnabled;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) data;
            loginEnabled = Boolean.TRUE.equals(map.get("loginEnabled"));
            paymentEnabled = Boolean.TRUE.equals(map.get("paymentEnabled"));
        } catch (Exception e) {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_031", "Payload không đúng định dạng."));
            return;
        }

        String error = client.getUserController().updateTotpPrefs(currentUser.getId(), loginEnabled, paymentEnabled);
        if (error == null) {
            currentUser.setTotpLoginEnabledRaw(loginEnabled);
            currentUser.setTotpPaymentEnabledRaw(paymentEnabled);
            client.sendResponse("UPDATE_TOTP_PREFS_SUCCESS", Map.of("loginEnabled", loginEnabled, "paymentEnabled", paymentEnabled, "message", "Tùy chọn TOTP đã được cập nhật."));
            log.info("TOTP prefs updated for user {}: login={}, payment={}", currentUser.getUserName(), loginEnabled, paymentEnabled);
        } else {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_032", error));
        }
    }
}