package org.example.demo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLogin = new FXMLLoader(MainApplication.class.getResource("Login.fxml"));
        FXMLLoader fxmlMainView = new FXMLLoader(MainApplication.class.getResource("MainView.fxml"));
        Scene sceneLogin = new Scene(fxmlLogin.load());
        Scene sceneMainView = new Scene(fxmlMainView.load());
        stage.setScene(sceneLogin);
        stage.setScene(sceneMainView);
        stage.show();
    }
}
