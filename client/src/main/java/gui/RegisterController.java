package gui;

import client.network.NetworkClient;
import model.User;
import network.NetworkMessage;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import static utils.ConsoleColors.*;

public class RegisterController implements Initializable {

    @FXML private TextField registerName;
    @FXML private TextField registerAccountName;
    @FXML private PasswordField registerPasswordAccount;
    @FXML private PasswordField confirmPasswordAccount;
    @FXML private ComboBox<String> registerRole;
    @FXML private Button registerButton;
    @FXML private Button changeLoginScene;

    private NetworkClient networkClient;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        registerRole.getItems().addAll("BIDDER", "SELLER");
        registerRole.getSelectionModel().selectFirst();
    }

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
        String role = registerRole.getValue();

        if (name.isEmpty() || username.isEmpty() || password.isEmpty() || role == null) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, "Missing Information", "Please fill in all fields!");
            return;
        }

        if (confirmPasswordAccount != null && !password.equals(confirmPass)) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, "Password Error", "Passwords do not match!");
            return;
        }

        String passwordRegex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{6,20}$";
        if (!password.matches(passwordRegex)) {
            String errorMsg = "Password is too weak!\n"
                    + "- 6 to 20 characters.\n"
                    + "- At least 1 uppercase letter (A-Z).\n"
                    + "- At least 1 lowercase letter (a-z).\n"
                    + "- At least 1 number (0-9).\n"
                    + "- At least 1 special character (@, $, !, %, *, ?, &).";

            AlertHelper.showAlert(Alert.AlertType.WARNING, "Invalid Password", errorMsg);
            return;
        }

        User newUser = new User("", username, password, name, role);

        if (networkClient != null) {
            networkClient.setOnMessageReceived(this::handleServerResponse);
            System.out.println("[System]: Sending registration data to server...");
            networkClient.sendMessage("REGISTER", newUser);

            registerButton.setDisable(true);
        } else {
            System.out.println("[Error]: " + RED + "NetworkClient not initialized" + RESET);
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Network Error", "Cannot connect to the server!");
        }
    }

    @FXML
    protected void onLoginViewButtonClick() {
        System.out.println("[Log]: Login UI view");
        MainApplication.setNewScene(MainApplication.rootLogin);
    }

    private void handleServerResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            registerButton.setDisable(false);

            String command = response.getCommand();
            Object data = response.getData();

            System.out.println(BLUE + "=== SERVER RESPONSE ===" + RESET);
            System.out.println("Command: " + YELLOW + command + RESET);
            System.out.println("Data: " + data);

            try {
                if ("REGISTER_SUCCESS".equals(command)) {
                    System.out.println("[System]: " + GREEN + "Registration successful. Initializing 2FA..." + RESET);

                    @SuppressWarnings("unchecked")
                    List<String> dataList = (List<String>) data;
                    String secretKey = dataList.get(0);
                    String qrUrl     = dataList.get(1);

                    Image qrImage = QRCodeHelper.generateQRCodeImage(qrUrl, 250, 250);

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Successfully Registered");
                    alert.setHeaderText("Enable 2FA through Google Authenticator");

                    String instructions = "Scan the following QR code to enable 2FA.\n\n"
                            + "Or enter this setup key manually:\n"
                            + YELLOW + secretKey + RESET;
                    alert.setContentText("Please secure your 2FA setup before proceeding.");

                    if (qrImage != null) {
                        ImageView imageView = new ImageView(qrImage);
                        alert.setGraphic(imageView);
                    }

                    alert.showAndWait();

                    clearFields();
                    MainApplication.setNewScene(MainApplication.rootLogin);

                } else if ("REGISTER_FAIL".equals(command) || "ERROR".equals(command)) {
                    String errorMsg = data != null ? data.toString() : "Unknown error from server";
                    System.out.println("[System]: " + RED + "Registration failed: " + errorMsg + RESET);
                    AlertHelper.showAlert(Alert.AlertType.ERROR, "Registration Failed", errorMsg);
                }
            } catch (Exception e) {
                System.out.println("[Error]: QR code generation error: " + RED + e.getMessage() + RESET);
                e.printStackTrace();
                AlertHelper.showAlert(Alert.AlertType.ERROR, "System Error", "Cannot process the 2FA setup data");
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
