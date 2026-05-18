package gui;

import client.network.NetworkClient;
import client.network.NetworkService;
import client.utils.ErrorParser;
import gui.process.AlertHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import model.user.User;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller đăng ký tài khoản mới.
 * 2FA KHÔNG còn được thiết lập ở đây; người dùng tự bật trong Settings.
 */
public class RegisterController {
    private static final Logger log =
            LoggerFactory.getLogger(RegisterController.class);

    @FXML private TextField   registerName;
    @FXML private TextField   registerAccountName;
    @FXML private PasswordField registerPasswordAccount;
    @FXML private PasswordField confirmPasswordAccount;
    @FXML private Button registerButton;
    @FXML private Button changeLoginScene;

    private NetworkClient networkClient;

    public void setNetworkClient(NetworkClient client) {
        this.networkClient = client;
    }

    @FXML
    protected void onRegisterButtonClick() {
        log.info("Registration process started.");
        String name        = registerName.getText().trim();
        String username    = registerAccountName.getText().trim();
        String password    = registerPasswordAccount.getText().trim();
        String confirmPass = confirmPasswordAccount != null
                ? confirmPasswordAccount.getText().trim() : "";

        if (name.isEmpty() || username.isEmpty() || password.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING,
                    "Thiếu thông tin", "Vui lòng điền đầy đủ các trường.");
            return;
        }

        if (confirmPasswordAccount != null && !password.equals(confirmPass)) {
            AlertHelper.showAlert(Alert.AlertType.WARNING,
                    "Mật khẩu không khớp", "Vui lòng kiểm tra lại mật khẩu.");
            return;
        }

        String passwordRegex =
                "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{6,20}$";
        if (!password.matches(passwordRegex)) {
            AlertHelper.showAlert(Alert.AlertType.WARNING,
                    "Mật khẩu yếu",
                    "Mật khẩu phải 6-20 ký tự, có ít nhất 1 chữ hoa, "
                            + "1 chữ thường, 1 số và 1 ký tự đặc biệt (@$!%*?&).");
            return;
        }

        setNetworkClient(NetworkService.get());
        if (networkClient == null) {
            AlertHelper.showAlert(Alert.AlertType.ERROR,
                    "Lỗi mạng", "Không thể kết nối tới máy chủ.");
            return;
        }

        registerButton.setDisable(true);
        networkClient.setOnMessageReceived(this::handleServerResponse);
        networkClient.sendMessage("REGISTER",
                new User("", username, password, name, "USER"));
    }

    @FXML
    protected void onLoginViewButtonClick() {
        clearFields();
        MainApplication.setNewScene(MainApplication.rootLogin);
    }

    private void handleServerResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            registerButton.setDisable(false);
            String command = response.getCommand();

            if ("REGISTER_SUCCESS".equals(command)) {
                log.info("Registration successful for new user.");
                AlertHelper.showAlert(Alert.AlertType.INFORMATION,
                        "Đăng ký thành công",
                        "Tài khoản đã được tạo!\n"
                                + "Bạn có thể bật bảo mật 2 lớp (2FA) trong mục Settings "
                                + "sau khi đăng nhập.");
                clearFields();
                MainApplication.setNewScene(MainApplication.rootLogin);

            } else if ("REGISTER_FAIL".equals(command)
                    || "ERROR".equals(command)) {
                String errorMsg = ErrorParser.parse(response.getData());
                log.warn("Registration failed: {}", errorMsg);
                AlertHelper.showAlert(Alert.AlertType.ERROR,
                        "Đăng ký thất bại", errorMsg);
            }
        });
    }

    private void clearFields() {
        registerName.clear();
        registerAccountName.clear();
        registerPasswordAccount.clear();
        if (confirmPasswordAccount != null) confirmPasswordAccount.clear();
    }
}