package gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
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

    public static void setNewScene(Parent k) throws IOException {
        primalStage.getScene().setRoot(k);
    }

    public void init() throws IOException {
        FXMLLoader fxmlLogin = new FXMLLoader(MainApplication.class.getResource("Login.fxml"));
        FXMLLoader fxmlRegister = new FXMLLoader(MainApplication.class.getResource("Register.fxml"));
        FXMLLoader fxmlMainView = new FXMLLoader(MainApplication.class.getResource("MainView.fxml"));

        rootLogin = fxmlLogin.load();
        rootRegister = fxmlRegister.load();
        rootMainView = fxmlMainView.load();

        ComboBox<String> registerRole = (ComboBox<String>) rootRegister.lookup("#registerRole");

        if(registerRole!=null){
            registerRole.getItems().clear();
            registerRole.getItems().addAll("Bidder","Seller","Admin");
        }
    }

    public void initProperties() throws IOException {
        InputStream input = MainApplication.class.getResourceAsStream("config.properties");
        if (input != null) {
            System.out.println("Reading properties file...");
            properties.load(input);
        }
    }

    @Override
    public void start(Stage stage) throws IOException {
        init();
        initProperties();
        primalStage = stage;
        Scene sceneLogin = new Scene(rootLogin);
        stage.setScene(sceneLogin);
        stage.show();
    }
}
