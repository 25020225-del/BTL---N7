package org.example.demo;

import client.NetworkClient; // Import anh shipper của chúng ta
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class MainApplication extends Application {
    public static Stage primalStage;
    public static Parent rootLogin;
    public static Parent rootRegister;
    public static Parent rootMainView;

    private Properties properties = new Properties();

    // BIẾN QUẢN LÝ MẠNG DÙNG CHUNG TOÀN APP
    public static NetworkClient networkClient;

    public static void setNewScene(Parent k) {
        if (primalStage != null && primalStage.getScene() != null) {
            primalStage.getScene().setRoot(k);
        }
    }

    public void initProperties() throws IOException {
        InputStream input = MainApplication.class.getResourceAsStream("config.properties");
        if (input != null) {
            System.out.println("Reading properties file...");
            properties.load(input);
        } else {
            System.err.println("Không tìm thấy file config.properties!");
        }
    }

    // ĐÃ SỬA: Dùng thông tin từ config để khởi tạo NetworkClient
    public void openClient() {
        // Lấy thông tin từ file config, nếu không có thì mặc định là localhost:6969
        String serverURL = properties.getProperty("serverURL", "localhost");
        int port = Integer.parseInt(properties.getProperty("serverPort", "6969"));

        System.out.println("Đang kết nối tới: " + serverURL + ":" + port);
        networkClient = new NetworkClient(serverURL, port);
    }

    public void init() throws IOException {
        FXMLLoader fxmlLogin = new FXMLLoader(MainApplication.class.getResource("Login.fxml"));
        FXMLLoader fxmlRegister = new FXMLLoader(MainApplication.class.getResource("Register.fxml"));
        FXMLLoader fxmlMainView = new FXMLLoader(MainApplication.class.getResource("MainView.fxml"));

        rootLogin = fxmlLogin.load();
        rootRegister = fxmlRegister.load();
        rootMainView = fxmlMainView.load();

        // CHÚ Ý TRỌNG ĐIỂM: Bơm mạng vào cho các Controller ngay sau khi load
        RegisterController registerCtrl = fxmlRegister.getController();
        if (registerCtrl != null) {
            registerCtrl.setNetworkClient(networkClient);
        }

        LoginController loginCtrl = fxmlLogin.getController();
        if (loginCtrl != null) {
            loginCtrl.setNetworkClient(networkClient);
        }

        // Đoạn code Combobox cũ của bạn (Nếu RegisterController đã tự khởi tạo combobox thì bạn có thể xóa đoạn này đi cho sạch code)
        /*
        ComboBox<String> registerRole = (ComboBox<String>) rootRegister.lookup("#registerRole");
        if(registerRole!=null){
            registerRole.getItems().clear();
            registerRole.getItems().addAll("Bidder","Seller","Admin");
        }
        */
    }

    @Override
    public void start(Stage stage) throws IOException {
        // THỨ TỰ CỰC KỲ QUAN TRỌNG: Đọc file Config -> Bật mạng -> Load giao diện
        initProperties();
        openClient();
        init();

        primalStage = stage;
        Scene sceneLogin = new Scene(rootLogin);
        stage.setScene(sceneLogin);
        stage.show();
    }
}