package gui;

import client.network.NetworkClient;
import gui.process.AlertHelper;
import gui.process.QRCodeHelper;
import model.User;
import network.NetworkMessage;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.List;

import static utils.ConsoleColors.*;

public class RegisterController {

    @FXML private TextField registerName;
    @FXML private TextField registerAccountName;
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
        System.out.println("[Log]: Registration process started");

        String name     = registerName.getText().trim();
        String username = registerAccountName.getText().trim();
        String password = registerPasswordAccount.getText().trim();
        String confirmPass = (confirmPasswordAccount != null) ? confirmPasswordAccount.getText().trim() : "";

        if (name.isEmpty() || username.isEmpty() || password.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, "Invalid information", "Please enter on every field");
            return;
        }

        if (confirmPasswordAccount != null && !password.equals(confirmPass)) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, "Invalid password", "Incorrect password");
            return;
        }

        String passwordRegex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{6,20}$";
        if (!password.matches(passwordRegex)) {
            String errorMsg = "Your password is too weak!\n"
                    + "- Must be 6-20 characters long.\n"
                    + "- Contains at least 1 capital letter (A-Z).\n"
                    + "- Contains at least 1 normal letter (a-z).\n"
                    + "- Contains at least a number (0-9).\n"
                    + "- Contains at least a special character (@, $, !, %, *, ?, &).";

            AlertHelper.showAlert(Alert.AlertType.WARNING, "Invalid password", errorMsg);
            return;
        }

        User newUser = new User("", username, password, name, "USER");

        if (networkClient != null) {
            networkClient.setOnMessageReceived(this::handleServerResponse);
            System.out.println("[System]: Sending registration data to server...");
            networkClient.sendMessage("REGISTER", newUser);

            registerButton.setDisable(true);
        } else {
            System.out.println("[System]: " + RED + "Network is not initialized" + RESET);
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Network Error", "Cannot connect to server");
        }
    }

    @FXML
    protected void onLoginViewButtonClick() {
        System.out.println("[Log]: Login UI view");
        clearFields();
        MainApplication.setNewScene(MainApplication.rootLogin);
    }

    private void handleServerResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            registerButton.setDisable(false);

            String command = response.getCommand();
            Object data = response.getData();

            try {
                if ("REGISTER_SUCCESS".equals(command)) {
                    System.out.println("[System]: " + GREEN + "Registration successful. Initializing 2FA..." + RESET);

                    @SuppressWarnings("unchecked")
                    List<String> dataList = (List<String>) data;
                    String secretKey = dataList.get(0);
                    String qrUrl     = dataList.get(1);

                    Image qrImage = QRCodeHelper.generateQRCodeImage(qrUrl, 250, 250);

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Successfully registered");
                    alert.setHeaderText("Enable 2FA through Google Authenticator");

                    String instructions = "Scan the following QR to enable 2FA.\n\n"
                            + "or manually enter the key:\n"
                            + secretKey;
                    alert.setContentText(instructions);

                    if (qrImage != null) {
                        ImageView imageView = new ImageView(qrImage);
                        alert.setGraphic(imageView);
                    }

                    alert.showAndWait();

                    clearFields();
                    MainApplication.setNewScene(MainApplication.rootLogin);

                } else if ("REGISTER_FAIL".equals(command) || "ERROR".equals(command)) {
                    String errorMsg = data != null ? data.toString() : "Unidentified error";
                    System.out.println("[System]: " + RED + "Registration failed: " + errorMsg + RESET);
                    AlertHelper.showAlert(Alert.AlertType.ERROR, "Registration failed", errorMsg);
                }
            } catch (Exception e) {
                System.out.println("[System]: QR code generation error: " + RED + e.getMessage() + RESET);
                e.printStackTrace();
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