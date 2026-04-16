package gui;

import client.NetworkClient;
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
            // MẸO QUAN TRỌNG: Gắn lại "tai nghe" cho LoginController ngay trước khi gửi.
            // Để đảm bảo khi Server trả lời, kết quả sẽ chạy vào file này chứ không chạy nhầm sang file Đăng ký.
            networkClient.setOnMessageReceived(this::handleServerResponse);

            // Tạo đối tượng User ảo (chỉ cần username và password) để gửi qua Jackson
            User loginAttempt = new User("", username, password, "", "");

            // Gửi lệnh lên Server
            networkClient.sendMessage("LOGIN", loginAttempt);
        } else {
            showAlert(Alert.AlertType.ERROR, "Lỗi mạng", "Chưa kết nối được với Server!");
        }
    }

    // --- SỰ KIỆN KHI BẤM NÚT "REGISTER" ---
    @FXML
    protected void onRegisterViewButtonClick() {
        System.out.println("[Log]: Register UI view");
        // Xóa trắng ô nhập liệu trước khi chuyển đi cho gọn gàng
        tenDangNhap.clear();
        matKhauDangNhap.clear();

        // Chuyển Scene
        MainApplication.setNewScene(MainApplication.rootRegister);
    }

    // --- KỊCH BẢN XỬ LÝ KHI SERVER TRẢ KẾT QUẢ VỀ ---
    private void handleServerResponse(NetworkMessage response) {
        String command = response.getCommand();

        if ("LOGIN_SUCCESS".equals(command)) {
            // Lấy thông tin User thật từ Server trả về (nếu nhóm bạn cần dùng Tên, Role để hiện lên giao diện)
            // Object userData = response.getData();

            System.out.println("[System]: Successfully logged in");

            // Chuyển sang màn hình MainView
            System.out.println("[System]: Main UI view");
            MainApplication.setNewScene(MainApplication.rootMainView);

        } else if ("LOGIN_FAIL".equals(command)) {
            showAlert(Alert.AlertType.ERROR, "Login Failed", response.getData().toString());
        }
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