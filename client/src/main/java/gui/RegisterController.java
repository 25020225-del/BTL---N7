package gui;

import client.network.NetworkClient;
import client.network.NetworkService;
import client.utils.ErrorParser;
import gui.process.AlertUtils;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import model.user.User;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller handling user registration workflow requirements. Encapsulates profile text inputs,
 * verifies client-side password entropy thresholds, and transmits identity registration records.
 */
public class RegisterController {
    private static final Logger log = LoggerFactory.getLogger(RegisterController.class);
    private static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{6,20}$";

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

    /**
     * Extracts input text elements, evaluates security password constraints via syntax matrices,
     * and sends registration packets upstream.
     */
    @FXML
    protected void onRegisterButtonClick() {
        log.info("Registration request transaction sequence initialized.");
        String name = registerName.getText().trim();
        String username = registerAccountName.getText().trim();
        String password = registerPasswordAccount.getText().trim();
        String confirmPass = confirmPasswordAccount != null ? confirmPasswordAccount.getText().trim() : "";

        if (name.isEmpty() || username.isEmpty() || password.isEmpty()) {
            AlertUtils.showWarning("Missing Information", "Please fill in all the required fields.");
            return;
        }

        if (confirmPasswordAccount != null && !password.equals(confirmPass)) {
            AlertUtils.showWarning("Password Mismatch", "Passwords do not match. Please verify and try again.");
            return;
        }

        if (!password.matches(PASSWORD_REGEX)) {
            AlertUtils.showWarning("Weak Password",
                    "Password must be 6-20 characters long and include at least "
                            + "one uppercase letter, one lowercase letter, one number, and one special character (@$!%*?&).");
            return;
        }

        setNetworkClient(NetworkService.get());
        if (networkClient == null) {
            AlertUtils.showError("Network Error", "Unable to connect to the server.");
            return;
        }

        registerButton.setDisable(true);
        networkClient.setOnMessageReceived(this::handleServerResponse);
        networkClient.sendMessage("REGISTER", java.util.Map.of(
                "userName", username,
                "password", password,
                "name", name,
                "role", "USER"
        ));
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
                log.info("Registration successful for new user identity profile context.");
                AlertUtils.showInfo("Registration Successful",
                        "Your account has been successfully created!\n"
                                + "You can activate Two-Factor Authentication (2FA) inside the Settings "
                                + "dashboard after logging in.");
                clearFields();
                MainApplication.setNewScene(MainApplication.rootLogin);

            } else if ("REGISTER_FAIL".equals(command) || "ERROR".equals(command)) {
                String errorMsg = ErrorParser.parse(response.getData());
                log.warn("Registration rejected by systemic verification parameters: {}", errorMsg);
                AlertUtils.showError("Registration Failed", errorMsg);
            }
        });
    }

    private void clearFields() {
        registerName.clear();
        registerAccountName.clear();
        registerPasswordAccount.clear();
        if (confirmPasswordAccount != null) {
            confirmPasswordAccount.clear();
        }
    }
}