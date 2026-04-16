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
import java.util.ResourceBundle;

public class RegisterController implements Initializable {

    @FXML private TextField registerName;
    @FXML private TextField registerAccountName;
    @FXML private PasswordField registerPasswordAccount;
    // ĐÃ THÊM: Ô nhập lại mật khẩu (Nhớ đặt fx:id trong Scene Builder là confirmPasswordAccount nhé)
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
        System.out.println("[Log]: Clicked on register button");
        String name = registerName.getText().trim();
        String username = registerAccountName.getText().trim();
        String password = registerPasswordAccount.getText().trim();

        // Tránh lỗi NullPointerException nếu chưa map confirmPasswordAccount
        String confirmPass = (confirmPasswordAccount != null) ? confirmPasswordAccount.getText().trim() : "";
        String role = registerRole.getValue();

        if (name.isEmpty() || username.isEmpty() || password.isEmpty() || role == null) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Vui lòng nhập đầy đủ các trường!");
            return;
        }

        // ĐÃ THÊM: Kiểm tra 2 mật khẩu có khớp nhau không
        if (confirmPasswordAccount != null && !password.equals(confirmPass)) {
            showAlert(Alert.AlertType.WARNING, "Lỗi mật khẩu", "Mật khẩu xác nhận không khớp!");
            return;
        }

        String passwordRegex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{6,20}$";

        if (!password.matches(passwordRegex)) {
            String errorMsg = "Mật khẩu quá yếu!\n"
                    + "- Dài từ 6-20 ký tự.\n"
                    + "- Chứa ít nhất 1 chữ in hoa (A-Z).\n"
                    + "- Chứa ít nhất 1 chữ in thường (a-z).\n"
                    + "- Chứa ít nhất 1 chữ số (0-9).\n"
                    + "- Chứa ít nhất 1 ký tự đặc biệt (@, $, !, %, *, ?, &).";

            showAlert(Alert.AlertType.WARNING, "Mật khẩu không hợp lệ", errorMsg);
            return;
        }

        User newUser = new User("", username, password, name, role);

        if (networkClient != null) {
            networkClient.setOnMessageReceived(this::handleServerResponse);
            System.out.println("[System] Sending data to server");
            networkClient.sendMessage("REGISTER", newUser);
            registerButton.setDisable(true);
        } else {
            showAlert(Alert.AlertType.ERROR, "Lỗi mạng", "Chưa kết nối được với Server!");
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

            System.out.println("=== SERVER RESPONSE ===");
            System.out.println("Command: " + command);
            System.out.println("Data: " + data);

            try {
                if ("REGISTER_SUCCESS".equals(command)) {
                    String qrUrl = (String) data;
                    System.out.println("Link QR: " + qrUrl);

                    Image qrImage = QRCodeHelper.generateQRCodeImage(qrUrl, 250, 250);

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Successfully Registered");
                    alert.setHeaderText("[Test]: Enable 2FA through Google Authenticator");
                    alert.setContentText("Scan the following QR code to enable");

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

                } else if ("REGISTER_FAIL".equals(command) || "ERROR".equals(command)) {
                    String errorMsg = data != null ? data.toString() : "Unknown error";
                    showAlert(Alert.AlertType.ERROR, "Failed", errorMsg);
                }
            } catch (Exception e) {
                System.out.println("[System]: QR code generation error:");
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "System Error", "Cannot draw QR code");
            }
        });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}