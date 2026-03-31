package org.example.demo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApplication extends Application {
    public static Stage primalStage;
    @Override
    public void start(Stage stage) throws IOException {
        primalStage = stage;
        FXMLLoader fxmlLogin = new FXMLLoader(MainApplication.class.getResource("Login.fxml"));
        Scene sceneLogin = new Scene(fxmlLogin.load());
        stage.setScene(sceneLogin);
        stage.show();
    }
}
