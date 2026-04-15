package gui;

import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.IOException;
import gui.ClientBidderController;
import javafx.scene.layout.VBox;

public class MainController {

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
        ClientBidderController.start();
    }

    @FXML
    protected void onRegisterButtonClick() throws IOException {
        ClientRegister.onRegisterButtonClick();
    }
}
