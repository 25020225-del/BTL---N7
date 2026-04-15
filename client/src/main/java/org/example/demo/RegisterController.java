package org.example.demo;

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
        String name = registerName.getText().trim();
        String username = registerAccountName.getText().trim();
        String password = registerPasswordAccount.getText().trim();
        String role = registerRole.getValue();

        if (name.isEmpty() || username.isEmpty() || password.isEmpty() || role == null) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Vui lòng nhập đầy đủ các trường!");
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
            networkClient.sendMessage("REGISTER", newUser);
            registerButton.setDisable(true);
        } else {
            showAlert(Alert.AlertType.ERROR, "Lỗi mạng", "Chưa kết nối được với Server!");
        }
    }

    @FXML
    protected void onLoginViewButtonClick() {
        System.out.println("Chuyển sang màn hình Đăng nhập...");
        MainApplication.setNewScene(MainApplication.rootLogin);
    }

    private void handleServerResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            registerButton.setDisable(false);

            String command = response.getCommand();
            Object data = response.getData();

            if ("REGISTER_SUCCESS".equals(command)) {

                // ==========================================
                // HIỂN THỊ MÃ QR ĐỂ NGƯỜI DÙNG QUÉT (2FA)
                // ==========================================
                String qrUrl = (String) data; // Ép kiểu data thành chuỗi URL
                Image qrImage = QRCodeHelper.generateQRCodeImage(qrUrl, 250, 250);

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Đăng ký thành công - QUAN TRỌNG");
                alert.setHeaderText("Bắt buộc: Cài đặt Bảo mật 2 lớp (2FA)");
                alert.setContentText("Hãy mở App Google/Microsoft Authenticator trên điện thoại và QUÉT MÃ QR NÀY NGAY BÂY GIỜ.\nBạn sẽ cần mã 6 số để đăng nhập ở lần tiếp theo.");

                // Gắn bức ảnh QR vào Pop-up
                if (qrImage != null) {
                    ImageView imageView = new ImageView(qrImage);
                    alert.setGraphic(imageView);
                }

                // Hiển thị và đợi người dùng bấm OK
                alert.showAndWait();

                // Dọn dẹp form
                registerName.clear();
                registerAccountName.clear();
                registerPasswordAccount.clear();

                // Đã mở comment: Chuyển luôn sang màn hình Login cho mượt
                MainApplication.setNewScene(MainApplication.rootLogin);

                // Bổ sung thêm || "ERROR".equals(command) để bắt cả lỗi từ Jackson/Server
            } else if ("REGISTER_FAIL".equals(command) || "ERROR".equals(command)) {
                String errorMsg = data != null ? data.toString() : "Lỗi không xác định";
                showAlert(Alert.AlertType.ERROR, "Thất bại", errorMsg);
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