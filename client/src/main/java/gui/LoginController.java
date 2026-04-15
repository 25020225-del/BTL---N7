package gui;

import client.NetworkClient;
import javafx.application.Platform; // ĐÃ THÊM: Bắt buộc phải có để cập nhật giao diện
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import model.User;
import network.NetworkMessage;

public class LoginController {

    // Khớp chính xác với fx:id trong file Login.fxml
    @FXML private TextField tenDangNhap;
    @FXML private PasswordField matKhauDangNhap;

    private NetworkClient networkClient;

    // Bơm mạng vào (được gọi từ MainApplication)
    public void setNetworkClient(NetworkClient client) {
        this.networkClient = client;
    }

    // --- SỰ KIỆN KHI BẤM NÚT "LOGIN" ---
    @FXML
    protected void onMainViewButtonClick() {
        String username = tenDangNhap.getText().trim();
        String password = matKhauDangNhap.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Vui lòng nhập Tài khoản và Mật khẩu!");
            return;
        }

        if (networkClient != null) {
            networkClient.setOnMessageReceived(this::handleServerResponse);
            User loginAttempt = new User("", username, password, "", "");
            networkClient.sendMessage("LOGIN", loginAttempt);
        } else {
            showAlert(Alert.AlertType.ERROR, "Lỗi mạng", "Chưa kết nối được với Server!");
        }
    }

    // --- SỰ KIỆN KHI BẤM NÚT "REGISTER" ---
    @FXML
    protected void onRegisterViewButtonClick() {
        System.out.println("[Log] Register UI view");
        tenDangNhap.clear();
        matKhauDangNhap.clear();
        MainApplication.setNewScene(MainApplication.rootRegister);
    }

    // --- KỊCH BẢN XỬ LÝ KHI SERVER TRẢ KẾT QUẢ VỀ ---
    private void handleServerResponse(NetworkMessage response) {
        // ĐÃ THÊM: Ép toàn bộ các lệnh cập nhật UI chạy trên luồng chính của JavaFX
        Platform.runLater(() -> {
            String command = response.getCommand();

            if ("LOGIN_SUCCESS".equals(command)) {
                System.out.println("[System]: Successfully logged in");

                // Dọn dẹp form trước khi sang trang mới
                tenDangNhap.clear();
                matKhauDangNhap.clear();

                MainApplication.setNewScene(MainApplication.rootMainView);

            } else if ("LOGIN_FAIL".equals(command) || "ERROR".equals(command)) {
                // Sẽ hiện Pop-up đỏ nếu nhập sai pass
                String errorMsg = response.getData() != null ? response.getData().toString() : "Đăng nhập thất bại!";
                showAlert(Alert.AlertType.ERROR, "Từ chối truy cập", errorMsg);
            }
        });
    }

    // --- HÀM TIỆN ÍCH HIỂN THỊ THÔNG BÁO ---
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}