package gui;

import client.network.NetworkService;
import client.utils.ErrorParser;
import javafx.scene.control.*;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import client.network.NetworkClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import gui.process.AlertHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import model.user.User;
import network.NetworkMessage;
import utils.JacksonConfig;

import java.util.Optional;

/**
 * Controller responsible for managing the user login interface.
 * It handles capturing user credentials, validating inputs, dispatching authentication
 * requests to the server, and routing the user to the appropriate dashboard upon success.
 */
public class LoginController {
    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    @FXML
    private Circle myava1;

    @FXML
    private TextField loginAccountName;

    @FXML
    private PasswordField loginPasswordAccount;

    @FXML
    private Button loginButton;

    private NetworkClient networkClient;

    // Configured ObjectMapper to ignore unknown properties, preventing crashes if the server
    // sends additional fields not mapped in the local User model.
    private final ObjectMapper mapper = JacksonConfig.mapper();

    /**
     * Injects the active network client instance into this controller.
     *
     * @param client The active {@link NetworkClient} connected to the server.
     */
    public void setNetworkClient(NetworkClient client) {
        this.networkClient = client;
    }

    /**
     * Handles the sign-in button click event.
     * Validates that the input fields are not empty, temporarily disables the login button
     * to prevent spamming, and sends the authentication payload to the server.
     */
    @FXML
    protected void onMainViewButtonClick() {
        String username = loginAccountName.getText().trim();
        String password = loginPasswordAccount.getText().trim();

        // Perform basic client-side validation
        if (username.isEmpty() || password.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, "Missing Information", "Please enter your Username and Password!");
            return;
        }

        // Fetch the global network client instance
        setNetworkClient(NetworkService.get());
        if (networkClient != null) {
            // Update UI state to indicate processing
            loginButton.setDisable(true);
            loginButton.setText("SIGNING IN...");

            // Register the callback to handle the server's authentication response
            networkClient.setOnMessageReceived(this::handleServerResponse);
            // Create a temporary User object holding the login credentials
            User loginAttempt = new User("", username, password, "");
            networkClient.sendMessage("LOGIN", loginAttempt);
        } else {
            log.warn("Cannot connect to server.");
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Network Error", "Cannot connect to the server");
        }
    }

    /**
     * Handles the navigation to the registration view when the user clicks the sign-up link.
     */
    @FXML
    protected void onRegisterViewButtonClick() {
        loginAccountName.clear();
        loginPasswordAccount.clear();
        MainApplication.setNewScene(MainApplication.rootRegister);
    }



    /**
     * Processes the server's response regarding the login attempt.
     * Must be executed on the JavaFX Application Thread (via Platform.runLater)
     * as it manipulates UI components.
     *
     * @param response The network message received from the server containing the login status.
     */
    private void handleServerResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            // Restore UI state
            loginButton.setDisable(false);
            loginButton.setText("SIGN IN");

            String command = response.getCommand();
            log.debug("Server Response: {}", command);

            if ("LOGIN_SUCCESS".equals(command)) {
                try {
                    // Deserialize the authenticated user data returned by the server
                    User loggedInUser = mapper.convertValue(response.getData(), User.class);

                    log.info("{} successfully logged in", loggedInUser.getUserName());

                    // Clear sensitive fields from memory
                    loginAccountName.clear();
                    loginPasswordAccount.clear();

                    // Delegate routing to the MainController based on the user's role
                    MainController.start(loggedInUser);
                } catch (Exception e) {
                    e.printStackTrace();
                    log.error("Login Error: {}", e.getMessage());
                }

            }
            else if ("LOGIN_NEED_TOTP".equals(command)) {
                showTotpDialog();  // ← thêm

            } else if ("ERROR".equals(command)) {
                String errorMsg = ErrorParser.parse(response.getData());
                AlertHelper.showAlert(Alert.AlertType.ERROR, "Login Failed", errorMsg);
            }
            else if ("LOGIN_FAIL".equals(command) || "ERROR".equals(command)) {
                // [FIXED]: Extract error message using ErrorParser
                String errorMsg = ErrorParser.parse(response.getData());

                log.warn("Login failed: {}", errorMsg);

                AlertHelper.showAlert(Alert.AlertType.ERROR, "Login Failed", errorMsg);
            }
        });
    }

    private void showTotpDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Xác thực 2 bước");
        dialog.setHeaderText("Nhập mã 6 số từ Google Authenticator");
        dialog.setContentText("Mã OTP:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(codeStr -> {
            try {
                int code = Integer.parseInt(codeStr.trim());
                networkClient.setOnMessageReceived(this::handleServerResponse);
                networkClient.sendMessage("VERIFY_TOTP", code);
            } catch (NumberFormatException e) {
                AlertHelper.showAlert(Alert.AlertType.ERROR,
                        "Lỗi", "Mã OTP phải là 6 chữ số.");
            }
        });
    }
}