package org.example.demo;

import com.sun.tools.javac.Main;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;

import java.io.IOException;

public class MainController {

    @FXML private TextField registerName;
    @FXML private TextField registerAccountName;
    @FXML private TextField registerPasswordAccount;

    @FXML
    protected void onLoginViewButtonClick() throws IOException {
        MainApplication.setNewScene(MainApplication.rootLogin);
    }

    @FXML
    protected void onRegisterViewButtonClick() throws IOException {
        TextField registerName = (TextField) MainApplication.rootRegister.lookup("#registerName");
        TextField registerAccountName = (TextField) MainApplication.rootRegister.lookup("#registerAccountName");
        PasswordField registerPasswordAccount = (PasswordField) MainApplication.rootRegister.lookup("#registerPasswordAccount");
        ComboBox<String> registerRole = (ComboBox<String>) MainApplication.rootRegister.lookup("#registerRole");
        registerName.setText("");
        registerAccountName.setText("");
        registerPasswordAccount.setText("");
        registerRole.setValue(null);
        MainApplication.setNewScene(MainApplication.rootRegister);
    }

    @FXML
    protected void onMainViewButtonClick() throws IOException {
        MainApplication.setNewScene(MainApplication.rootMainView);
    }

    @FXML
    protected void onRegisterButtonClick() throws IOException {
        FXMLLoader fxmlRegister = new FXMLLoader(MainApplication.class.getResource("Register.fxml"));
        Scene sceneRegister = new Scene(fxmlRegister.load());
    }
}
