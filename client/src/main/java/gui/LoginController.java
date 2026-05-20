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
 * Login Controller.
 * Handles two scenarios: standard login and two-factor authentication (REQUIRE_2FA).
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
                    "Missing Information",
                    "Please enter your Username and Password!");
            return;
        }

        setNetworkClient(NetworkService.get());
        if (networkClient == null) {
            AlertHelper.showAlert(Alert.AlertType.ERROR,
                    "Network Error", "Unable to connect to the server.");
            return;
        }

        loginButton.setDisable(true);
        loginButton.setText("LOGGING IN...");

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
            loginButton.setText("LOGIN");

            String command = response.getCommand();
            log.debug("Server Response: {}", command);

            switch (command) {
                case "LOGIN_SUCCESS"       -> handleLoginSuccess(response);
                case "REQUIRE_2FA"         -> handleRequire2FA();
                case "VERIFY_2FA_SUCCESS"  -> handleLoginSuccess(response);
                case "LOGIN_FAIL", "ERROR" -> {
                    String err = ErrorParser.parse(response.getData());
                    log.warn("Login failed: {}", err);
                    AlertHelper.showAlert(Alert.AlertType.ERROR,
                            "Login Failed", err);
                }
                default -> log.warn("Unknown command during login: {}", command);
            }
        });
    }

    /**
     * Server requires 2FA authentication — displays the OTP input dialog.
     */
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
                        // Maintain callback; server will return VERIFY_2FA_SUCCESS or ERROR
                        networkClient.sendMessage("VERIFY_2FA",
                                Map.of("code", code));
                        loginButton.setDisable(true);
                        loginButton.setText("VERIFYING...");
                    } catch (NumberFormatException e) {
                        // FIX: Unlock the button if the user enters a non-numeric value
                        loginButton.setDisable(false);
                        loginButton.setText("LOGIN");

                        AlertHelper.showAlert(Alert.AlertType.WARNING,
                                "Invalid OTP",
                                "The OTP must be a 6-digit number. Please try again.");
                    }
                },
                () -> {
                    // User clicked Cancel — do not send anything, reset UI states
                    loginButton.setDisable(false);
                    loginButton.setText("LOGIN");
                }
        );
    }

    /**
     * Successfully logged in (triggered by either LOGIN_SUCCESS or VERIFY_2FA_SUCCESS).
     */
    private void handleLoginSuccess(NetworkMessage response) {
        try {
            User loggedInUser = mapper.convertValue(response.getData(), User.class);
            log.info("{} successfully logged in.", loggedInUser.getUserName());
            loginAccountName.clear();
            loginPasswordAccount.clear();

            networkClient.setOnMessageReceived(null); // ← FIX: unregister trước khi rời scene

            MainController.start(loggedInUser);
        } catch (Exception e) {
            log.error("Error processing LOGIN_SUCCESS: {}", e.getMessage(), e);
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Error", "Failed to load account data.");
        }
    }
}