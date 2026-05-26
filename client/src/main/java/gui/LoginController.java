package gui;

import client.network.NetworkClient;
import client.network.NetworkService;
import client.utils.ErrorParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import gui.process.AlertUtils;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import model.user.User;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JacksonConfig;

import java.util.Map;
import java.util.Optional;

/**
 * Headless orchestration controller managing user authentication and password-reset domains.
 *
 * <p>This controller manages three in-place UI panels within the same scene:
 * <ol>
 *   <li><strong>loginPane</strong>   — standard username/password login</li>
 *   <li><strong>forgotTotpPane</strong> — Step 1: enter username + TOTP to obtain a reset token</li>
 *   <li><strong>newPasswordPane</strong> — Step 2: set a new password using the reset token</li>
 * </ol>
 *
 * <p>The forgot-password flow never leaves the login screen and never requires email.
 * It relies exclusively on TOTP ownership as proof of identity.</p>
 */
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    // ── Existing login pane ──────────────────────────────────────────────────
    @FXML private Circle myava1;
    @FXML private VBox loginPane;
    @FXML private TextField loginAccountName;
    @FXML private PasswordField loginPasswordAccount;
    @FXML private Button loginButton;
    @FXML private Hyperlink forgotPasswordLink;

    @FXML private VBox forgotTotpPane;
    @FXML private TextField resetIdentifierField;
    @FXML private TextField resetTotpCodeField;
    @FXML private Button verifyTotpResetButton;
    @FXML private Button backToLoginBtn1;
    @FXML private Label totpResetErrorLabel;

    @FXML private VBox newPasswordPane;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmNewPasswordField;
    @FXML private Button resetPasswordButton;
    @FXML private Button backToLoginBtn2;
    @FXML private Label newPasswordErrorLabel;
    private NetworkClient networkClient;
    private final ObjectMapper mapper = JacksonConfig.mapper();

    /** Holds the single-use reset token returned by Step 1, consumed in Step 2. */
    private String pendingResetToken = null;

    public void setNetworkClient(NetworkClient client) {
        this.networkClient = client;
    }

    @FXML
    protected void onMainViewButtonClick() {
        String username = loginAccountName.getText().trim();
        String password = loginPasswordAccount.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            AlertUtils.showWarning("Thiếu thông tin", "Vui lòng nhập Tên đăng nhập và Mật khẩu!");
            return;
        }

        setNetworkClient(NetworkService.get());
        if (networkClient == null) {
            AlertUtils.showError("Network Error", "Cannot connect to the server.");
            return;
        }

        loginButton.setDisable(true);
        loginButton.setText("Logging in...");

        networkClient.setOnMessageReceived(this::handleServerResponse);
        networkClient.sendMessage("LOGIN", Map.of("userName", username, "password", password));
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
            loginButton.setText("Log In");

            String command = response.getCommand();
            log.debug("Server Response: {}", command);

            switch (command) {
                case "LOGIN_SUCCESS"       -> handleLoginSuccess(response);
                case "REQUIRE_2FA"         -> handleRequire2FA();
                case "VERIFY_2FA_SUCCESS"  -> handleLoginSuccess(response);
                case "LOGIN_FAIL", "ERROR" -> {
                    String err = ErrorParser.parse(response.getData());
                    log.warn("Login failed: {}", err);
                    AlertUtils.showError("Log In failed", err);
                }
                default -> log.warn("Unknown command during login: {}", command);
            }
        });
    }

    private void handleRequire2FA() {
        log.info("Server requires 2FA. Showing OTP dialog...");
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("2FA verification");
        dialog.setHeaderText("Your account has 2FA enabled.");
        dialog.setContentText("Enter 6 digits code provided by Google Authenticator:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresentOrElse(otpText -> {
            try {
                int code = Integer.parseInt(otpText.trim());
                networkClient.sendMessage("VERIFY_2FA", Map.of("code", code));
                loginButton.setDisable(true);
                loginButton.setText("Authenticating...");
            } catch (NumberFormatException e) {
                loginButton.setDisable(false);
                loginButton.setText("Log In");
                AlertUtils.showWarning("Invalid OTP", "OTP code must be a 6 digits code. Please try again.");
            }
        }, () -> {
            loginButton.setDisable(false);
            loginButton.setText("Log In");
        });
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
            AlertUtils.showError("Error", "Cannot load account data.");
        }
    }

    /** Called when user clicks "Quên mật khẩu?" hyperlink on the login pane. */
    @FXML
    protected void onForgotPasswordClick() {
        showPane(forgotTotpPane);
        totpResetErrorLabel.setText("");
        resetIdentifierField.clear();
        resetTotpCodeField.clear();
    }

    @FXML
    protected void onBackToLoginFromStep1() {
        showPane(loginPane);
        pendingResetToken = null;
    }

    @FXML
    protected void onBackToLoginFromStep2() {
        showPane(loginPane);
        pendingResetToken = null;
        newPasswordField.clear();
        confirmNewPasswordField.clear();
    }

    /**
     * Sends the TOTP verification request for account reset.
     * On server success, transitions to Step 2 (new password pane).
     */
    @FXML
    protected void onVerifyTotpForReset() {
        String identifier = resetIdentifierField.getText().trim();
        String totpRaw = resetTotpCodeField.getText().trim();
        totpResetErrorLabel.setText("");

        if (identifier.isEmpty()) {
            totpResetErrorLabel.setText("Please enter username.");
            return;
        }

        int totpCode;
        try {
            totpCode = Integer.parseInt(totpRaw);
            if (totpRaw.length() != 6) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            totpResetErrorLabel.setText("TOTP code must be a 6 digits code.");
            return;
        }

        setNetworkClient(NetworkService.get());
        if (networkClient == null) {
            totpResetErrorLabel.setText("Cannot connect to the server.");
            return;
        }

        verifyTotpResetButton.setDisable(true);
        verifyTotpResetButton.setText("Authenticating...");

        networkClient.setOnMessageReceived(this::handleTotpResetResponse);
        networkClient.sendMessage("VERIFY_TOTP_FOR_RESET", Map.of(
                "identifier", identifier,
                "totpCode", totpCode
        ));
    }

    private void handleTotpResetResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            verifyTotpResetButton.setDisable(false);
            verifyTotpResetButton.setText("Authenticate");

            String command = response.getCommand();
            if ("TOTP_RESET_VERIFIED".equals(command)) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) response.getData();
                    pendingResetToken = (String) data.get("resetToken");
                    log.info("[RESET] Step 1 success. Transitioning to new-password pane.");
                    networkClient.setOnMessageReceived(null);
                    showPane(newPasswordPane);
                    newPasswordErrorLabel.setText("");
                    newPasswordField.clear();
                    confirmNewPasswordField.clear();
                } catch (Exception e) {
                    log.error("[RESET] Error reading resetToken: {}", e.getMessage(), e);
                    totpResetErrorLabel.setText("System Error. Please try again.");
                }
            } else {
                // ERROR or unexpected command
                String err = ErrorParser.parse(response.getData());
                totpResetErrorLabel.setText(err);
                log.warn("[RESET] Step 1 failed: {}", err);
            }
        });
    }

    /**
     * Sends the new password to the server using the previously obtained reset token.
     * The server enforces password policy (min 8 chars, uppercase, digit, special char).
     */
    @FXML
    protected void onResetPassword() {
        String newPass = newPasswordField.getText();
        String confirm = confirmNewPasswordField.getText();
        newPasswordErrorLabel.setText("");

        if (newPass.isEmpty() || confirm.isEmpty()) {
            newPasswordErrorLabel.setText("Please enter new password.");
            return;
        }
        if (!newPass.equals(confirm)) {
            newPasswordErrorLabel.setText("New password cannot be the same as old one.");
            return;
        }

        // Client-Side Entropy Synchronization Alignment
        if (newPass.length() < 8) {
            newPasswordErrorLabel.setText("Password must contain at least 8 characters.");
            return;
        }
        if (!newPass.matches(".*[A-Z].*")) {
            newPasswordErrorLabel.setText("Password must contain at least an uppercase letter (A-Z).");
            return;
        }
        if (!newPass.matches(".*[0-9].*")) {
            newPasswordErrorLabel.setText("Password must contain at least a digit (0-9).");
            return;
        }
        if (!newPass.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;':\",./<>?].*")) {
            newPasswordErrorLabel.setText("Password must contain at least a special character (!@#$%^&*...).");
            return;
        }

        if (pendingResetToken == null) {
            newPasswordErrorLabel.setText("Resetting password session is expired. Please restart.");
            showPane(forgotTotpPane);
            return;
        }

        setNetworkClient(NetworkService.get());
        if (networkClient == null) {
            newPasswordErrorLabel.setText("Cannot connect to the server.");
            return;
        }

        resetPasswordButton.setDisable(true);
        resetPasswordButton.setText("Updating...");

        networkClient.setOnMessageReceived(this::handleResetPasswordResponse);
        networkClient.sendMessage("RESET_PASSWORD", Map.of(
                "resetToken", pendingResetToken,
                "newPassword", newPass
        ));
    }

    private void handleResetPasswordResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            resetPasswordButton.setDisable(false);
            resetPasswordButton.setText("Reset Password");

            String command = response.getCommand();
            if ("RESET_PASSWORD_SUCCESS".equals(command)) {
                pendingResetToken = null;
                networkClient.setOnMessageReceived(null);

                newPasswordField.clear();
                confirmNewPasswordField.clear();

                AlertUtils.showInfo(
                        "Success",
                        "Your password has been updated.\nYou can log in with new password."
                );

                showPane(loginPane);
                log.info("[RESET] Password reset flow completed successfully.");
            } else {
                String err = ErrorParser.parse(response.getData());
                newPasswordErrorLabel.setText(err);
                log.warn("[RESET] Step 2 failed: {}", err);
                if (err != null && err.contains("hết hạn")) {
                    pendingResetToken = null;
                    showPane(forgotTotpPane);
                    totpResetErrorLabel.setText("Session expired. Please try TOTP again.");
                }
            }
        });
    }

    /**
     * Shows exactly one of the three panels by toggling visibility and managed state.
     * Using {@code setManaged(false)} prevents hidden nodes from consuming layout space.
     */
    private void showPane(VBox paneToShow) {
        for (VBox pane : new VBox[]{loginPane, forgotTotpPane, newPasswordPane}) {
            boolean visible = (pane == paneToShow);
            pane.setVisible(visible);
            pane.setManaged(visible);
        }
    }
}