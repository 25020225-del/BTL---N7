package org.example.demo;

import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.io.IOException;

public class ClientLogin {
    protected static void onLoginViewButtonClick() throws IOException {
        TextField loginAccountName = (TextField) MainApplication.rootLogin.lookup("#loginAccountName");
        PasswordField loginPasswordAccount = (PasswordField) MainApplication.rootLogin.lookup("#loginPasswordAccount");
        loginAccountName.setText("");
        loginPasswordAccount.setText("");
        MainApplication.setNewScene(MainApplication.rootLogin);
    }
}
