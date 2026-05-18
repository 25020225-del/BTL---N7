package gui;

import client.network.NetworkClient;
import client.network.NetworkService;
import client.utils.ErrorParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import gui.process.AlertHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.shape.Circle;
import model.user.User;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JacksonConfig;

import java.util.Map;
import java.util.Optional;

/**
 * Controller đăng nhập.
 * Xử lý 2 kịch bản: đăng nhập thường và đăng nhập có 2FA (REQUIRE_2FA).
 */
public class LoginController {
    private static final Logger log =
            LoggerFactory.getLogger(LoginController.class);

    @FXML private Circle        myava1;
    @FXML private TextField     loginAccountName;
    @FXML private PasswordField loginPasswordAccount;
    @FXML private Button        loginButton;

    private NetworkClient networkClient;
    private final ObjectMapper mapper = JacksonConfig.mapper();

    public void setNetworkClient(NetworkClient client) {
        this.networkClient = client;
    }

    @FXML
    protected void onMainViewButtonClick() {
        String username = loginAccountName.getText().trim();
        String password = loginPasswordAccount.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING,
                    "Thiếu thông tin",
                    "Vui lòng nhập Tên đăng nhập và Mật khẩu!");
            return;
        }

        setNetworkClient(NetworkService.get());
        if (networkClient == null) {
            AlertHelper.showAlert(Alert.AlertType.ERROR,
                    "Lỗi mạng", "Không thể kết nối tới máy chủ.");
            return;
        }

        loginButton.setDisable(true);
        loginButton.setText("ĐANG ĐĂNG NHẬP...");

        networkClient.setOnMessageReceived(this::handleServerResponse);
        networkClient.sendMessage("LOGIN",
                new User("", username, password, ""));
    }

    @FXML
    protected void onRegisterViewButtonClick() {
        loginAccountName.clear();
        loginPasswordAccount.clear();
        MainApplication.setNewScene(MainApplication.rootRegister);
    }

    // ── Response handler ─────────────────────────────────────────────

    private void handleServerResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            loginButton.setDisable(false);
            loginButton.setText("ĐĂNG NHẬP");

            String command = response.getCommand();
            log.debug("Server Response: {}", command);

            switch (command) {
                case "LOGIN_SUCCESS"    -> handleLoginSuccess(response);
                case "REQUIRE_2FA"      -> handleRequire2FA();
                case "VERIFY_2FA_SUCCESS" -> handleLoginSuccess(response);
                case "LOGIN_FAIL", "ERROR" -> {
                    String err = ErrorParser.parse(response.getData());
                    log.warn("Login failed: {}", err);
                    AlertHelper.showAlert(Alert.AlertType.ERROR,
                            "Đăng nhập thất bại", err);
                }
                default -> log.warn("Unknown command during login: {}", command);
            }
        });
    }

    /**
     * Server yêu cầu xác thực 2FA — hiển thị dialog nhập OTP.
     */
    private void handleRequire2FA() {
        log.info("Server requires 2FA. Showing OTP dialog...");

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Xác thực 2 lớp (2FA)");
        dialog.setHeaderText("Tài khoản của bạn được bảo vệ bằng 2FA.");
        dialog.setContentText("Nhập mã 6 số từ Google Authenticator:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresentOrElse(
                otpText -> {
                    try {
                        int code = Integer.parseInt(otpText.trim());
                        // Giữ nguyên callback; server sẽ trả VERIFY_2FA_SUCCESS hoặc ERROR
                        networkClient.sendMessage("VERIFY_2FA",
                                Map.of("code", code));
                        loginButton.setDisable(true);
                        loginButton.setText("ĐANG XÁC THỰC...");
                    } catch (NumberFormatException e) {
                        AlertHelper.showAlert(Alert.AlertType.WARNING,
                                "OTP không hợp lệ",
                                "Mã OTP phải là 6 chữ số. Vui lòng thử lại.");
                    }
                },
                () -> {
                    // Người dùng bấm Cancel — không gửi gì, reset UI
                    loginButton.setDisable(false);
                    loginButton.setText("ĐĂNG NHẬP");
                }
        );
    }

    /**
     * Đăng nhập thành công (có thể đến từ LOGIN_SUCCESS hoặc VERIFY_2FA_SUCCESS).
     */
    @SuppressWarnings("unchecked")
    private void handleLoginSuccess(NetworkMessage response) {
        try {
            User loggedInUser = mapper.convertValue(
                    response.getData(), User.class);

            log.info("{} successfully logged in.", loggedInUser.getUserName());
            loginAccountName.clear();
            loginPasswordAccount.clear();

            MainController.start(loggedInUser);
        } catch (Exception e) {
            log.error("Error processing LOGIN_SUCCESS: {}", e.getMessage(), e);
            AlertHelper.showAlert(Alert.AlertType.ERROR,
                    "Lỗi", "Không thể tải dữ liệu tài khoản.");
        }
    }
}