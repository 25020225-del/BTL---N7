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
 * Register Controller.
 * 2FA is NO longer configured during registration; users can enable it manually via Settings.
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
                    "Missing Information", "Please fill in all the required fields.");
            return;
        }

        if (confirmPasswordAccount != null && !password.equals(confirmPass)) {
            AlertHelper.showAlert(Alert.AlertType.WARNING,
                    "Password Mismatch", "Passwords do not match. Please verify and try again.");
            return;
        }

        String passwordRegex =
                "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{6,20}$";
        if (!password.matches(passwordRegex)) {
            AlertHelper.showAlert(Alert.AlertType.WARNING,
                    "Weak Password",
                    "Password must be 6-20 characters long and include at least "
                            + "one uppercase letter, one lowercase letter, one number, and one special character (@$!%*?&).");
            return;
        }

        setNetworkClient(NetworkService.get());
        if (networkClient == null) {
            AlertHelper.showAlert(Alert.AlertType.ERROR,
                    "Network Error", "Unable to connect to the server.");
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
                        "Registration Successful",
                        "Your account has been successfully created!\n"
                                + "You can activate Two-Factor Authentication (2FA) inside the Settings "
                                + "dashboard after logging in.");
                clearFields();
                MainApplication.setNewScene(MainApplication.rootLogin);

            } else if ("REGISTER_FAIL".equals(command)
                    || "ERROR".equals(command)) {
                String errorMsg = ErrorParser.parse(response.getData());
                log.warn("Registration failed: {}", errorMsg);
                AlertHelper.showAlert(Alert.AlertType.ERROR,
                        "Registration Failed", errorMsg);
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