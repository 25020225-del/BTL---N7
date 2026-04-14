package org.example.demo;

import client.NetworkClient; // Import file mạng của bạn
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApplication extends Application {

    // Các biến chứa giao diện dùng chung (kỹ thuật Pre-load của bạn)
    public static Stage primalStage;
    public static Parent rootLogin;
    public static Parent rootRegister;
    public static Parent rootMainView;

    // --- Biến chứa kết nối Mạng dùng chung cho toàn App ---
    public static NetworkClient networkClient;

    // Hàm chuyển màn hình nhanh (gọi từ bất kỳ Controller nào)
    public static void setNewScene(Parent newRoot) {
        if (primalStage != null && primalStage.getScene() != null) {
            primalStage.getScene().setRoot(newRoot);
        }
    }

    @Override
    public void start(Stage stage) throws IOException {
        primalStage = stage;

        // 1. KHỞI TẠO MẠNG NGAY KHI VỪA BẬT APP
        networkClient = new NetworkClient("localhost", 6969);

        // 2. LOAD GIAO DIỆN ĐĂNG NHẬP
        FXMLLoader fxmlLogin = new FXMLLoader(MainApplication.class.getResource("Login.fxml"));
        rootLogin = fxmlLogin.load();

        // Lấy LoginController ra và truyền mạng vào
        LoginController loginCtrl = fxmlLogin.getController();
        if (loginCtrl != null) {
            loginCtrl.setNetworkClient(networkClient);
        }

        // 3. LOAD GIAO DIỆN ĐĂNG KÝ
        FXMLLoader fxmlRegister = new FXMLLoader(MainApplication.class.getResource("Register.fxml"));
        rootRegister = fxmlRegister.load();
        // Bơm mạng vào cho RegisterController để nó biết đường gửi JSON
        RegisterController registerCtrl = fxmlRegister.getController();
        if (registerCtrl != null) {
            registerCtrl.setNetworkClient(networkClient);
        }

        // 4. LOAD GIAO DIỆN CHÍNH (MAIN VIEW)
        FXMLLoader fxmlMainView = new FXMLLoader(MainApplication.class.getResource("MainView.fxml"));
        rootMainView = fxmlMainView.load();
        // Tương tự, bơm mạng vào cho MainController sau này
        // MainController mainCtrl = fxmlMainView.getController();
        // if (mainCtrl != null) mainCtrl.setNetworkClient(networkClient);

        // ĐÃ XÓA ĐOẠN COMBOBOX (.lookup) VÌ REGISTER_CONTROLLER ĐÃ TỰ LO RỒI

        // 5. HIỂN THỊ MÀN HÌNH ĐẦU TIÊN LÀ LOGIN
        Scene scene = new Scene(rootLogin);
        stage.setScene(scene);
        stage.setTitle("Hệ thống Đấu giá BTL---N7");
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        System.out.println("Ứng dụng Client đang tắt...");
        super.stop();
    }
}