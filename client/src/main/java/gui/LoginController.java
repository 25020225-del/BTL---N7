package gui;

import client.NetworkClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import model.User;
import network.NetworkMessage;

import static utils.ConsoleColors.*;

public class LoginController {

    @FXML private TextField loginAccountName;
    @FXML private PasswordField loginPasswordAccount;

    private NetworkClient networkClient;

    public void setNetworkClient(NetworkClient client) {
        this.networkClient = client;
    }

    @FXML
    protected void onMainViewButtonClick() {
        String username = loginAccountName.getText().trim();
        String password = loginPasswordAccount.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, "Missing Information", "Please enter your Username and Password!");
            return;
        }

        if (networkClient != null) {
            networkClient.setOnMessageReceived(this::handleServerResponse);
            User loginAttempt = new User("", username, password, "", "");
            networkClient.sendMessage("LOGIN", loginAttempt);
        } else {
            System.out.println("[Error]: " + RED + "Cannot connect to the server!" + RESET);
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Network Error", "Cannot connect to the server!");
        }
    }

    @FXML
    protected void onRegisterViewButtonClick() {
        System.out.println("[Log]: Register UI view");
        loginAccountName.clear();
        loginPasswordAccount.clear();
        MainApplication.setNewScene(MainApplication.rootRegister);
    }

    private void handleServerResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            String command = response.getCommand();

            if ("LOGIN_SUCCESS".equals(command)) {
                System.out.println("[System]: " + GREEN + "Successfully logged in" + RESET);

                loginAccountName.clear();
                loginPasswordAccount.clear();

                System.out.println("[Log]: Main UI view");
                MainApplication.setNewScene(MainApplication.rootMainView);

            } else if ("LOGIN_FAIL".equals(command) || "ERROR".equals(command)) {
                String errorMsg = response.getData() != null ? response.getData().toString() : "Username or password is incorrect!";

                System.out.println("[System]: " + RED + "Login failed: " + errorMsg + RESET);

                AlertHelper.showAlert(Alert.AlertType.ERROR, "Login Failed", errorMsg);
            }
        });
    }
}