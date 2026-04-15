package gui;

import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.IOException;

public class MainController {

    @FXML private TextField registerName;
    @FXML private TextField registerAccountName;
    @FXML private TextField registerPasswordAccount;

    @FXML
    protected void onLoginViewButtonClick() throws IOException {
        ClientLogin.onLoginViewButtonClick();
    }

    @FXML
    protected void onRegisterViewButtonClick() throws IOException {
        ClientRegister.onRegisterViewButtonClick();
    }

    @FXML
    protected void onMainViewButtonClick() throws IOException {
        MainApplication.setNewScene(MainApplication.rootMainView);
    }

    @FXML
    protected void onRegisterButtonClick() throws IOException {
        ClientRegister.onRegisterButtonClick();
    }
}
