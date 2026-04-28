package gui;

import client.network.NetworkClient;
import gui.process.AlertHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import model.User;
import network.NetworkMessage;
import com.fasterxml.jackson.databind.DeserializationFeature;

import static utils.ConsoleColors.*;

public class LoginController {

    @FXML private TextField loginAccountName;
    @FXML private PasswordField loginPasswordAccount;

    private NetworkClient networkClient;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public void setNetworkClient(NetworkClient client) {
        this.networkClient = client;
    }

    @FXML
    protected void onMainViewButtonClick() {
        try{

            MainController.start(new Bidder());
        }
        catch (IOException e){}
        String username = loginAccountName.getText().trim();
        String password = loginPasswordAccount.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, "Missing Information", "Please enter your Username and Password!");
            return;
        }

        setNetworkClient(MainApplication.networkClient);

        if (networkClient != null) {
            networkClient.setOnMessageReceived(this::handleServerResponse);
            User loginAttempt = new User("", username, password, "");
            networkClient.sendMessage("LOGIN", loginAttempt);
            //System.out.println("[Log]: Login successful!");
            /*Platform.runLater(() -> {
                try {
                    MainController.start(loginAttempt);
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            });*/
        } else {
            System.out.println("[Error]: " + RED + "Cannot connect to the server" + RESET);
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Network Error", "Cannot connect to the server");
        }
    }

    @FXML
    protected void onRegisterViewButtonClick() {
        System.out.println("[Log]: Register UI view");
        loginAccountName.clear();
        loginPasswordAccount.clear();
        MainApplication.setNewScene(MainApplication.rootRegister);
    }

    private void handleServerResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            String command = response.getCommand();
            System.out.println("[Log]: Server Response: " + command);

            if ("LOGIN_SUCCESS".equals(command)) {

                try {
                    // Decipher user from server
                    User loggedInUser = mapper.convertValue(response.getData(), User.class);

                    System.out.println("[Log]: " + GREEN + loggedInUser.getName() + " successfully logged in" + RESET);

                    loginAccountName.clear();
                    loginPasswordAccount.clear();

                    MainController.start(loggedInUser);
                } catch (Exception e) {
                    System.out.println("[Error]: Login error: " + RED + e.getMessage() + RESET);
                }

            } else if ("LOGIN_FAIL".equals(command) || "ERROR".equals(command)) {
                String errorMsg = response.getData() != null ? response.getData().toString() : "Username or password is incorrect!";

                System.out.println("[System]: " + RED + "Login failed: " + errorMsg + RESET);

                AlertHelper.showAlert(Alert.AlertType.ERROR, "Login Failed", errorMsg);
            }
        });
    }
}