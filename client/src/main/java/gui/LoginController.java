package gui;

import client.network.NetworkClient;
import client.network.NetworkService;
import client.utils.ErrorParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import gui.process.AlertUtils;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.shape.Circle;
import model.user.User;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JacksonConfig;

import java.util.Map;
import java.util.Optional;

/**
 * Headless orchestration controller managing user authentication domains.
 * Directs raw state form collection, maps asymmetric dynamic multi-stage security
 * challenges, and encapsulates transactional tokens safely upon verification.
 */
public class LoginController {
    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    @FXML private Circle myava1;
    @FXML private TextField loginAccountName;
    @FXML private PasswordField loginPasswordAccount;
    @FXML private Button loginButton;

    private NetworkClient networkClient;
    private final ObjectMapper mapper = JacksonConfig.mapper();

    public void setNetworkClient(NetworkClient client) {
        this.networkClient = client;
    }

    /**
     * Extracts structural security claims from text controls and triggers
     * an upstream authentication request across the active network channel.
     */
    @FXML
    protected void onMainViewButtonClick() {
        String username = loginAccountName.getText().trim();
        String password = loginPasswordAccount.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            AlertUtils.showWarning("Missing Information", "Please enter your Username and Password!");
            return;
        }

        setNetworkClient(NetworkService.get());
        if (networkClient == null) {
            AlertUtils.showError("Network Error", "Unable to connect to the server.");
            return;
        }

        loginButton.setDisable(true);
        loginButton.setText("LOGGING IN...");

        networkClient.setOnMessageReceived(this::handleServerResponse);
        networkClient.sendMessage("LOGIN", new User("", username, password, ""));
    }

    @FXML
    protected void onRegisterViewButtonClick() {
        loginAccountName.clear();
        loginPasswordAccount.clear();
        MainApplication.setNewScene(MainApplication.rootRegister);
    }

    private void handleServerResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            loginButton.setDisable(false);
            loginButton.setText("LOGIN");

            String command = response.getCommand();
            log.debug("Server Response: {}", command);

            switch (command) {
                case "LOGIN_SUCCESS" -> handleLoginSuccess(response);
                case "REQUIRE_2FA" -> handleRequire2FA();
                case "VERIFY_2FA_SUCCESS" -> handleLoginSuccess(response);
                case "LOGIN_FAIL", "ERROR" -> {
                    String err = ErrorParser.parse(response.getData());
                    log.warn("Login failed: {}", err);
                    AlertUtils.showError("Login Failed", err);
                }
                default -> log.warn("Unknown command during login: {}", command);
            }
        });
    }

    private void handleRequire2FA() {
        log.info("Server requires 2FA. Showing OTP dialog...");

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Two-Factor Authentication (2FA)");
        dialog.setHeaderText("Your account is protected by 2FA.");
        dialog.setContentText("Enter the 6-digit code from Google Authenticator:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresentOrElse(
                otpText -> {
                    try {
                        int code = Integer.parseInt(otpText.trim());
                        networkClient.sendMessage("VERIFY_2FA", Map.of("code", code));
                        loginButton.setDisable(true);
                        loginButton.setText("VERIFYING...");
                    } catch (NumberFormatException e) {
                        loginButton.setDisable(false);
                        loginButton.setText("LOGIN");
                        AlertUtils.showWarning("Invalid OTP", "The OTP must be a 6-digit number. Please try again.");
                    }
                },
                () -> {
                    loginButton.setDisable(false);
                    loginButton.setText("LOGIN");
                }
        );
    }

    private void handleLoginSuccess(NetworkMessage response) {
        try {
            User loggedInUser = mapper.convertValue(response.getData(), User.class);
            log.info("{} successfully logged in.", loggedInUser.getUserName());
            loginAccountName.clear();
            loginPasswordAccount.clear();

            networkClient.setOnMessageReceived(null);
            MainController.start(loggedInUser);
        } catch (Exception e) {
            log.error("Error processing LOGIN_SUCCESS: {}", e.getMessage(), e);
            AlertUtils.showError("Error", "Failed to load account data.");
        }
    }
}