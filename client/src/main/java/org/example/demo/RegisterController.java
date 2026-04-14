package org.example.demo;

import client.NetworkClient; // Đảm bảo đúng đường dẫn package của bạn
import model.User;
import network.NetworkMessage;
import javafx.application.Platform; // QUAN TRỌNG: Import thêm thư viện này
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.util.ResourceBundle;

public class RegisterController implements Initializable {

    // --- LIÊN KẾT VỚI CÁC fx:id TRONG FILE FXML ---
    @FXML private TextField registerName;
    @FXML private TextField registerAccountName;
    @FXML private PasswordField registerPasswordAccount;
    @FXML private ComboBox<String> registerRole;
    @FXML private Button registerButton;
    @FXML private Button changeLoginScene;

    // Đối tượng quản lý mạng dùng chung
    private NetworkClient networkClient;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Cài đặt các lựa chọn cho ComboBox Role
        registerRole.getItems().addAll("BIDDER", "SELLER");
        registerRole.getSelectionModel().selectFirst();
    }

    // --- NHẬN NETWORK CLIENT TỪ MAIN APPLICATION ---
    public void setNetworkClient(NetworkClient client) {
        this.networkClient = client;
        // ĐÃ XÓA: Không đặt setOnMessageReceived ở đây nữa để tránh ghi đè
    }

    // --- SỰ KIỆN KHI BẤM NÚT "REGISTER" ---
    @FXML
    protected void onRegisterButtonClick() {
        String name = registerName.getText().trim();
        String username = registerAccountName.getText().trim();
        String password = registerPasswordAccount.getText().trim();
        String role = registerRole.getValue();

        // 1. Kiểm tra không được để trống
        if (name.isEmpty() || username.isEmpty() || password.isEmpty() || role == null) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Vui lòng nhập đầy đủ các trường!");
            return;
        }

        String passwordRegex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{6,20}$";

        if (!password.matches(passwordRegex)) {
            String errorMsg = "Mật khẩu quá yếu!\n"
                    + "- Dài t 6-20 ký tự.\n"
                    + "- Chứa ít nhất 1 chữ in hoa (A-Z).\n"
                    + "- Chứa ít nhất 1 chữ in thường (a-z).\n"
                    + "- Chứa ít nhất 1 chữ số (0-9).\n"
                    + "- Chứa ít nhất 1 ký tự đặc biệt (@, $, !, %, *, ?, &).";

            showAlert(Alert.AlertType.WARNING, "Mật khẩu không hợp lệ", errorMsg);
            return; // Dừng lại, không gửi lên Server
        }

        // 3. Đóng gói thành User
        User newUser = new User("", username, password, name, role);

        if (networkClient != null) {
            // FIX LỖI 1: Gắn "tai nghe" ngay trước khi gửi để đảm bảo Form Đăng ký là người nhận
            networkClient.setOnMessageReceived(this::handleServerResponse);

            // Gửi dữ liệu đi
            networkClient.sendMessage("REGISTER", newUser);

            // Tạm khóa nút đăng ký để tránh người dùng bấm liên tục nhiều lần
            registerButton.setDisable(true);
        } else {
            showAlert(Alert.AlertType.ERROR, "Lỗi mạng", "Chưa kết nối được với Server!");
        }
    }

    // --- SỰ KIỆN KHI BẤM NÚT "LOGIN" Ở MÀN HÌNH ĐĂNG KÝ ---
    @FXML
    protected void onLoginViewButtonClick() {
        System.out.println("Chuyển sang màn hình Đăng nhập...");
        MainApplication.setNewScene(MainApplication.rootLogin);
    }

    // --- KỊCH BẢN XỬ LÝ KHI SERVER TRẢ KẾT QUẢ VỀ ---
    private void handleServerResponse(NetworkMessage response) {
        // FIX LỖI 2: Ép luồng xử lý giao diện phải chạy trên JavaFX Thread
        Platform.runLater(() -> {
            // Mở khóa lại nút Đăng ký
            registerButton.setDisable(false);

            String command = response.getCommand();
            Object data = response.getData();

            if ("REGISTER_SUCCESS".equals(command)) {
                showAlert(Alert.AlertType.INFORMATION, "Thành công!", "Đăng ký thành công tài khoản!");

                // Xóa trắng các ô nhập liệu cho gọn gàng
                registerName.clear();
                registerAccountName.clear();
                registerPasswordAccount.clear();

                // (Tùy chọn) Chuyển luôn sang màn hình Login sau khi đăng ký thành công
                // MainApplication.setNewScene(MainApplication.rootLogin);

            } else if ("REGISTER_FAIL".equals(command)) {
                // Ép kiểu data về String để hiển thị thông báo lỗi (VD: "Tên đăng nhập đã tồn tại")
                String errorMsg = data != null ? data.toString() : "Lỗi không xác định";
                showAlert(Alert.AlertType.ERROR, "Thất bại", errorMsg);
            }
        });
    }

    // --- HÀM TIỆN ÍCH BẬT THÔNG BÁO POP-UP ---
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}