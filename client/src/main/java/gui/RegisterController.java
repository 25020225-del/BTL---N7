package gui;

import client.NetworkClient;
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

public class RegisterController implements Initializable {

    public static final String ANSI_RESET  = "\u001B[0m";
    public static final String ANSI_RED    = "\u001B[31m";
    public static final String ANSI_GREEN  = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE   = "\u001B[34m";

    @FXML private TextField registerName;
    @FXML private TextField registerAccountName;
    @FXML private PasswordField registerPasswordAccount;
    @FXML private PasswordField confirmPasswordAccount;
    @FXML private ComboBox<String> registerRole;
    @FXML private Button registerButton;
    @FXML private Button changeLoginScene;

    private NetworkClient networkClient;

    @Override
    public void initialize(URL location,ResourceBundle resources) {
        registerRole.getItems().addAll("BIDDER","SELLER");
        registerRole.getSelectionModel().selectFirst();
    }

    public void setNetworkClient(NetworkClient client){networkClient = client;}

    @FXML
    protected void onRegisterButtonClick() {
        System.out.println("[Log]: Clicked on register button");
        String name     = registerName.getText().trim();
        String username = registerAccountName.getText().trim();
        String password = registerPasswordAccount.getText().trim();

        String confirmPass = (confirmPasswordAccount != null) ? confirmPasswordAccount.getText().trim() : "";

        String role = registerRole.getValue();

        if (name.isEmpty() || username.isEmpty() || password.isEmpty() || role == null) {
            showAlert(Alert.AlertType.WARNING, "Missing Information", "Please fill in all fields!");
            return;
        }

        if (confirmPasswordAccount != null && !password.equals(confirmPass)) {
            showAlert(Alert.AlertType.WARNING, "Password Error", "Passwords do not match!");
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

            showAlert(Alert.AlertType.WARNING, "Invalid Password", errorMsg);
            return;
        }

        User newUser = new User("", username, password, name, role);

        if (networkClient != null) {
            networkClient.setOnMessageReceived(this::handleServerResponse);
            System.out.println("[System]: Sending data to server...");
            networkClient.sendMessage("REGISTER", newUser);
            registerButton.setDisable(true);
        } else {
            showAlert(Alert.AlertType.ERROR, "Network Error", "Cannot connect to the server!");
        }
    }

    @FXML
    protected void onLoginViewButtonClick() {
        System.out.println("[System]: Login UI view");
        MainApplication.setNewScene(MainApplication.rootLogin);
    }

    private void handleServerResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            registerButton.setDisable(false);

            String command = response.getCommand();
            Object data = response.getData();

            System.out.println("=== SERVER RESPONSE ===");
            System.out.println("Command: " + command);
            System.out.println("Data: "    + data);

            try {
                if ("REGISTER_SUCCESS".equals(command)) {
                    @SuppressWarnings("unchecked")
                    List<String> dataList = (List<String>) data;

                    String secretKey = dataList.get(0);
                    String qrUrl     = dataList.get(1);

                    System.out.println("[System]: Secret Key: " + secretKey);
                    System.out.println("[System]: Link QR: "    + qrUrl);

                    Image qrImage = QRCodeHelper.generateQRCodeImage(qrUrl, 250, 250);

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Successfully Registered");
                    alert.setHeaderText("Enable 2FA through Google Authenticator");

                    String instructions = "Scan the following QR code to enable 2FA.\n\n"
                            + "Or enter this setup key manually:\n"
                            + secretKey;
                    alert.setContentText(instructions);

                    if (qrImage != null) {
                        ImageView imageView = new ImageView(qrImage);
                        alert.setGraphic(imageView);
                    }

                    alert.showAndWait();

                    registerName.clear();
                    registerAccountName.clear();
                    registerPasswordAccount.clear();
                    if (confirmPasswordAccount != null) confirmPasswordAccount.clear();

                    MainApplication.setNewScene(MainApplication.rootLogin);

                } else if ("REGISTER_FAIL".equals(command)||"ERROR".equals(command)) {
                    String errorMsg = data != null ? data.toString() : "Unknown error";
                    showAlert(Alert.AlertType.ERROR, "Registration Failed", errorMsg);
                }
            } catch (Exception e) {
                System.out.println(ANSI_RED + "[Error]: QR code generation error:" + ANSI_RESET);
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "System Error", "Cannot process the 2FA setup data");
            }
        });
    }

    private void showAlert(Alert.AlertType type,String title,String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}