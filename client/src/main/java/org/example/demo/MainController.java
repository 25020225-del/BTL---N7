package org.example.demo;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

import java.io.IOException;

public class MainController {
    @FXML
    private Label welcomeText;

    @FXML
    protected void onHelloButtonClick() throws IOException {
        FXMLLoader fxmlMainView = new FXMLLoader(MainApplication.class.getResource("MainView.fxml"));
        Scene sceneMainView = new Scene(fxmlMainView.load());
        MainApplication.primalStage.setScene(sceneMainView);
    }

    @FXML
    protected void onRegisterButtonClick() throws IOException {
        FXMLLoader fxmlRegister = new FXMLLoader(MainApplication.class.getResource("Register.fxml"));
        Scene sceneRegister = new Scene(fxmlRegister.load());
    }

    @FXML
    protected void onLoginViewButtonClick() throws IOException {
        FXMLLoader fxmlLoginView = new FXMLLoader(MainApplication.class.getResource("Login.fxml"));
        Scene sceneLogin = new Scene(fxmlLoginView.load());
        MainApplication.primalStage.setScene(sceneLogin);
    }

    @FXML
    protected void onRegisterViewButtonClick() throws IOException {
        FXMLLoader fxmlRegisterView = new FXMLLoader(MainApplication.class.getResource("Register.fxml"));
        Scene sceneRegister = new Scene(fxmlRegisterView.load());
        Parent root = sceneRegister.getRoot();
        ChoiceBox<String> combo = (ChoiceBox<String>) root.lookup("#registerRole");
        if (combo != null) {
            combo.getItems().clear(); // Nên xóa danh sách cũ trước khi add để tránh bị lặp dữ liệu
            combo.getItems().addAll("Bidder", "Seller", "Admin");
        }
        MainApplication.primalStage.setScene(sceneRegister);
    }
}
