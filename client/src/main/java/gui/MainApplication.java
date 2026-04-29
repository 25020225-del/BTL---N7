package gui;

import client.network.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static utils.ConsoleColors.*;

public class MainApplication extends Application {

    public static Stage primalStage;
    public static Parent rootLogin;
    public static Parent rootRegister;
    public static Parent rootMainView;

    private Properties properties = new Properties();

    public static NetworkClient networkClient;

    public static void main(String[] args) {

        System.out.println(GREEN + "=================================");
        System.out.println(        "|                               |");
        System.out.println(        "|       CLIENT LOG TABLE        |");
        System.out.println(        "|                               |");
        System.out.println(        "=================================" + RESET);
        launch(args);
    }

    public static void setNewScene(Parent k) {
        if(k==null){
            System.out.println("parent is null");
        }
        if (primalStage != null && primalStage.getScene() != null) {
            primalStage.getScene().setRoot(k);
        }
    }

    public void initProperties() throws IOException {
        InputStream input = MainApplication.class.getResourceAsStream("config.properties");
        if (input != null) {
            System.out.println("[System]: Reading configuration file...");
            properties.load(input);
        } else {
            System.out.println("[Error]: " + RED + "Cannot find config.properties" + RESET);
        }
    }

    public void init() throws IOException {

        FXMLLoader fxmlLogin = new FXMLLoader(MainApplication.class.getResource("Login.fxml"));
        FXMLLoader fxmlRegister = new FXMLLoader(MainApplication.class.getResource("Register.fxml"));

        rootLogin = fxmlLogin.load();
        rootRegister = fxmlRegister.load();

        RegisterController registerCtrl = fxmlRegister.getController();
        if (registerCtrl != null) {
            registerCtrl.setNetworkClient(networkClient);
        }

        LoginController loginCtrl = fxmlLogin.getController();
        if (loginCtrl != null) loginCtrl.setNetworkClient(networkClient);

        //ComboBox<String> registerRole = (ComboBox<String>) rootRegister.lookup("#registerRole");
        //if (registerRole != null) {
        //    registerRole.getItems().clear();
        //    registerRole.getItems().addAll("Bidder", "Seller");
        //    registerRole.getSelectionModel().selectFirst();
        //}
    }

    @Override
    public void start(Stage stage) throws IOException {
        primalStage = stage;
        initProperties();

        VBox loadingLayout = new VBox(20);
        loadingLayout.setAlignment(Pos.CENTER);
        ProgressIndicator spinner = new ProgressIndicator(); // Loading icon
        Label statusLabel = new Label("Connecting to server...");
        statusLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #333333;");
        loadingLayout.getChildren().addAll(spinner, statusLabel);

        Scene loadingScene = new Scene(loadingLayout, 800, 600);
        stage.setTitle("N7 Auction System - Connecting...");
        stage.setScene(loadingScene);
        stage.show();

        primalStage.setOnCloseRequest(event -> {
            System.out.println("[System]: Closing application...");
            // Clean UI threads
            javafx.application.Platform.exit();
            // Kill process
            System.exit(0);
        });

        new Thread(() -> {
            networkClient = ServerDiscovery.establishConnection(properties);

            Platform.runLater(() -> {
                try {
                    init();
                    // Switch to login UI
                    Scene sceneLogin = new Scene(rootLogin);
                    stage.setTitle("N7 Auction System - Client");
                    stage.setScene(sceneLogin);

                    System.out.println("[Log]: " + GREEN + "Application started successfully" + RESET);

                    if (!networkClient.isConnected()) {
                        gui.process.AlertHelper.showAlert(javafx.scene.control.Alert.AlertType.WARNING,
                                "Network Issue", "Cannot connect to server. Running offline version.");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }).start();
    }
}
