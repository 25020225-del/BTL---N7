package server.handler;

import controller.UserController;
import exception.AuctionExceptions;
import network.ErrorPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.user.User;
import network.NetworkMessage;
import server.ClientHandler;
import utils.JacksonConfig;

public class AuthHandler implements CommandHandler {
    private static final Logger log = LoggerFactory.getLogger(AuthHandler.class);
    private final ObjectMapper mapper = JacksonConfig.mapper();

    @Override
    public void handle(NetworkMessage message, ClientHandler client) throws Exception {
        String command = message.getCommand();

        if ("LOGIN".equals(command)) {
            processLogin(message.getData(), client);
        } else if ("REGISTER".equals(command)) {
            processRegister(message.getData(), client);
        } else if ("LOGOUT".equals(command)) {
            processLogout(client);
        } else if ("VERIFY_TOTP".equals(command)) {      // ← thêm dòng này
            processVerifyTotp(message.getData(), client);}
        else {
            throw new AuctionExceptions.InvalidPayloadException("Lệnh xác thực không hợp lệ.");
        }
    }

    private void processLogout(ClientHandler client) {
        String oldName = client.getClientName();
        client.setUser(null);
        client.setClientName("#Guest" + ClientHandler.getcNC());
        ClientHandler.incrementcNC();

        log.info("{} signed out and reverted to {}", oldName, client.getClientName());
    }

    private void processLogin(Object data, ClientHandler client) throws Exception {
        User loginAttempt = mapper.convertValue(data, User.class);
        User user = client.getUserController().loginCheckPassword(
                loginAttempt.getUserName(), loginAttempt.getPassword());

        if (user == null) {
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_AUTH_003", "Sai tên đăng nhập hoặc mật khẩu."));
            return;
        }

        if (user.isTotpEnabled()) {
            // Lưu tạm user vào ClientHandler, chờ bước verify OTP
            client.setPendingUser(user);
            client.sendResponse("LOGIN_NEED_TOTP", null);
        } else {
            // Tài khoản không bật TOTP → đăng nhập luôn (nếu muốn cho phép)
            client.setUser(user);
            client.setClientName(user.getUserName());
            client.sendResponse("LOGIN_SUCCESS", user);
        }

//    private void processLogin(Object data, ClientHandler client) throws Exception {
//        User loginAttempt;
//        try {
//            loginAttempt = mapper.convertValue(data, User.class);
//        } catch (IllegalArgumentException e) {
//            throw new AuctionExceptions.InvalidPayloadException("Dữ liệu đăng nhập không đúng định dạng.");
//        }
//
//        User user = client.getUserController().login(loginAttempt.getUserName(), loginAttempt.getPassword());
//
//        if (user != null) {
//            client.setClientName(user.getUserName());
//            client.setUser(user);
//            client.sendResponse("LOGIN_SUCCESS", user);
//        } else {
//            // Lỗi nghiệp vụ (Sai pass/user), trả thẳng ErrorPayload
//            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_003", "Sai tên đăng nhập hoặc mật khẩu."));
//        }
   }

    private void processRegister(Object data, ClientHandler client) throws Exception {
        User regUser;
        try {
            regUser = mapper.convertValue(data, User.class);
        } catch (IllegalArgumentException e) {
            throw new AuctionExceptions.InvalidPayloadException("Dữ liệu đăng ký không đúng định dạng.");
        }

        UserController.RegisterResult result = client.getUserController().register(
                regUser.getUserName(),
                regUser.getPassword(),
                regUser.getName(),
                regUser.getRole()
        );

        if (result.success()) {
            String[] responseData = {result.secretKey(), result.qrUrl()};
            client.sendResponse("REGISTER_SUCCESS", responseData);
            client.setClientName(regUser.getUserName());
        } else {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_005", result.errorMessage()));
        }
    }
    private void processVerifyTotp(Object data, ClientHandler client) throws Exception {
        User pending = client.getPendingUser();

        if (pending == null) {
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_AUTH_010", "Phiên xác thực đã hết hạn. Vui lòng đăng nhập lại."));
            return;
        }

        int code;
        try {
            code = mapper.convertValue(data, Integer.class);
        } catch (Exception e) {
            throw new AuctionExceptions.InvalidPayloadException("Mã OTP không đúng định dạng.");
        }

        String secret = client.getUserController().getTotpSecret(pending.getUserName());

        if (secret == null) {
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_AUTH_011", "Không tìm thấy cấu hình 2FA."));
            return;
        }

        if (client.getUserController().verifyTotp(secret, code)) {
            client.setPendingUser(null);
            client.setUser(pending);
            client.setClientName(pending.getUserName());
            client.sendResponse("LOGIN_SUCCESS", pending);
        } else {
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_AUTH_012", "Mã OTP không hợp lệ hoặc đã hết hạn."));
        }
    }
//
//    private void processRegister(Object data, ClientHandler client) throws Exception {
//        User regUser;
//        try {
//            regUser = mapper.convertValue(data, User.class);
//        } catch (IllegalArgumentException e) {
//            throw new AuctionExceptions.InvalidPayloadException("Dữ liệu đăng ký không đúng định dạng.");
//        }
//
//        String result = client.getUserController().register(
//                regUser.getUserName(),
//                regUser.getPassword(),
//                regUser.getName(),
//                regUser.getRole()
//        );
//
//        if (result != null && result.startsWith("SUCCESS|")) {
//            String[] parts = result.split("\\|");
//            String secretKey = parts[1];
//            String qrUrl = parts[2];
//            String[] responseData = {secretKey, qrUrl};
//
//            client.sendResponse("REGISTER_SUCCESS", responseData);
//            client.setClientName(regUser.getUserName());
//        } else {
//            // Lỗi trùng lặp user hoặc lỗi DB, UserController đã trả về câu thông báo thân thiện
//            client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_005", result));
//        }
//    }
}