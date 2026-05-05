package gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import client.network.NetworkClient;
import gui.process.AlertHelper;
import gui.process.QRCodeHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import model.user.User;
import network.NetworkMessage;

import java.util.List;

import static utils.ConsoleColors.*;

/**
 * Controller responsible for handling new user registrations.
 * All new public registrations are defaulted to the standard "USER" role.
 */
public class RegisterController {
    private static final Logger log = LoggerFactory.getLogger(RegisterController.class);

    @FXML
    private TextField registerName;
    @FXML
    private TextField registerAccountName;
    @FXML
    private PasswordField registerPasswordAccount;
    @FXML
    private PasswordField confirmPasswordAccount;
    @FXML
    private Button registerButton;
    @FXML
    private Button changeLoginScene;

    private NetworkClient networkClient;

    public void setNetworkClient(NetworkClient client) {
        this.networkClient = client;
    }

    /**
     * Handles the registration button click event.
     * Validates input fields and sends the registration payload to the server.
     */
    @FXML
    protected void onRegisterButtonClick() {
        log.info("Registration process started.");

        String name = registerName.getText().trim();
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

        // Unify all new sign-ups to the generic "USER" role
        User newUser = new User("", username, password, name, "USER");

        if (networkClient != null) {
            networkClient.setOnMessageReceived(this::handleServerResponse);
            log.info("Sending registration data to server...");
            networkClient.sendMessage("REGISTER", newUser);

            registerButton.setDisable(true);
        } else {
            log.warn("Network is not initialized.");
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Network Error", "Cannot connect to server");
        }
    }

    /**
     * Switches the view back to the Login screen.
     */
    @FXML
    protected void onLoginViewButtonClick() {
        clearFields();
        MainApplication.setNewScene(MainApplication.rootLogin);
    }

    /**
     * Processes the server's response regarding the registration attempt.
     * Handles 2FA setup upon successful registration.
     *
     * @param response The network message received from the server.
     */
    private void handleServerResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            registerButton.setDisable(false);

            String command = response.getCommand();
            Object data = response.getData();

            try {
                if ("REGISTER_SUCCESS".equals(command)) {
                    log.info("Registration successful. Initializing 2FA...");

                    @SuppressWarnings("unchecked")
                    List<String> dataList = (List<String>) data;
                    String secretKey = dataList.get(0);
                    String qrUrl = dataList.get(1);

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
                    log.warn("Registration failed: {}", errorMsg);
                    AlertHelper.showAlert(Alert.AlertType.ERROR, "Registration failed", errorMsg);
                }
            } catch (Exception e) {
                log.error("QR Code Generation Error: {}", e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Clears all input fields in the registration form.
     */
    private void clearFields() {
        registerName.clear();
        registerAccountName.clear();
        registerPasswordAccount.clear();
        if (confirmPasswordAccount != null) confirmPasswordAccount.clear();
    }
}