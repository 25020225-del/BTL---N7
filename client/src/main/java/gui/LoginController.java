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
            AlertHelper.showAlert(Alert.AlertType.WARNING, "Missing Information", "Please enter your Username and Password!");
            return;
        }

        if (networkClient != null) {
            networkClient.setOnMessageReceived(this::handleServerResponse);
            User loginAttempt = new User("", username, password, "", "");
            networkClient.sendMessage("LOGIN", loginAttempt);
        } else {
            // Thay thế bằng AlertHelper và dịch sang tiếng Anh
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Network Error", "Cannot connect to the server!");
        }
    }

    @FXML
    protected void onRegisterViewButtonClick() {
        System.out.println("[System]: Register UI view");
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
                // Thay thế bằng AlertHelper
                AlertHelper.showAlert(Alert.AlertType.ERROR, "Login Failed", errorMsg);
            }
        });
    }
}