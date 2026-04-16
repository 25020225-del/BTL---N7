package gui;

import client.NetworkClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import model.User;
import network.NetworkMessage;

public class LoginController {

    @FXML private TextField tenDangNhap;
    @FXML private PasswordField matKhauDangNhap;

    private NetworkClient networkClient;

    public void setNetworkClient(NetworkClient client) {
        this.networkClient = client;
    }

    @FXML
    protected void onMainViewButtonClick() {
        String username = tenDangNhap.getText().trim();
        String password = matKhauDangNhap.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Vui lòng nhập Tài khoản và Mật khẩu!");
            return;
        }

        if (networkClient != null) {
            networkClient.setOnMessageReceived(this::handleServerResponse);
            User loginAttempt = new User("", username, password, "", "");
            networkClient.sendMessage("LOGIN", loginAttempt);
        } else {
            showAlert(Alert.AlertType.ERROR, "Lỗi mạng", "Chưa kết nối được với Server!");
        }
    }

    @FXML
    protected void onRegisterViewButtonClick() {
        System.out.println("[Log]: Register UI view");
        tenDangNhap.clear();
        matKhauDangNhap.clear();
        MainApplication.setNewScene(MainApplication.rootRegister);
    }

    private void handleServerResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            String command = response.getCommand();

            if ("LOGIN_SUCCESS".equals(command)) {
                System.out.println("[System]: Successfully logged in");

                tenDangNhap.clear();
                matKhauDangNhap.clear();

                System.out.println("[System]: Main UI view");
                MainApplication.setNewScene(MainApplication.rootMainView);

            } else if ("LOGIN_FAIL".equals(command) || "ERROR".equals(command)) {
                String errorMsg = response.getData() != null ? response.getData().toString() : "Username or password is incorrect!";
                showAlert(Alert.AlertType.ERROR, "Login Failed", errorMsg);
            }
        });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}