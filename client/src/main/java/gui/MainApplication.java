package gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import client.network.NetworkClient;
import client.network.ServerDiscovery;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static utils.ConsoleColors.*;

/**
 * The main entry point for the JavaFX client application.
 * This class manages the primary application window (Stage), handles the initial
 * background network connection to the server, and loads the foundational UI components.
 */
public class MainApplication extends Application {
    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    // Global UI references to allow seamless scene switching
    public static Stage primalStage;
    public static Parent rootLogin;
    public static Parent rootRegister;
    public static Parent rootMainView;

    private Properties properties = new Properties();

    // The global network client session used across the entire application
    public static NetworkClient networkClient;

    /**
     * The standard Java main method.
     * It prints a startup banner to the console and launches the JavaFX application lifecycle.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {

        System.out.println(GREEN + "===================================================================");
        System.out.println(        "|                                                                 |");
        System.out.println(        "|                               LOG                               |");
        System.out.println(        "|                                                                 |");
        System.out.println(        "===================================================================" + RESET);
        launch(args);
    }

    /**
     * Dynamically swaps the root node of the primary active scene.
     * This utility is widely used by controllers for navigating between different views
     * (e.g., from Login to the Main Dashboard).
     *
     * @param k The new Parent node (FXML layout) to display.
     */
    public static void setNewScene(Parent k) {
        if (k == null) {
            log.warn("Parent is null");
        }
        if (primalStage != null && primalStage.getScene() != null) {
            primalStage.getScene().setRoot(k);
        }
    }

    /**
     * Loads the configuration properties from the local 'config.properties' file.
     * This file typically contains fallback server addresses and API keys.
     *
     * @throws IOException If the properties file cannot be found or read.
     */
    public void initProperties() throws IOException {
        InputStream input = MainApplication.class.getResourceAsStream("config.properties");
        if (input != null) {
            log.info("Reading configuration file...");
            properties.load(input);
        } else {
            log.error("Cannot find config.properties.");
        }
    }

    /**
     * Initializes the foundational FXML layouts (Login and Register)
     * and injects the globally initialized network client into their respective controllers.
     *
     * @throws IOException If the FXML files cannot be loaded.
     */
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
    }

    /**
     * The primary entry point for the JavaFX UI lifecycle.
     * Displays a loading screen while establishing a background network connection.
     * Once connected, it transitions to the login screen.
     *
     * @param stage The primary stage (window) provided by the JavaFX runtime.
     * @throws IOException If initialization resources fail to load.
     */
    @Override
    public void start(Stage stage) throws IOException {
        primalStage = stage;
        initProperties();

        // Construct a temporary Loading Screen UI dynamically
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

        // Handle the native OS window close event (clicking the "X" button)
        primalStage.setOnCloseRequest(event -> {
            log.info("Closing application...");
            // Clean UI threads
            javafx.application.Platform.exit();
            // Kill process completely to ensure no background socket threads leak
            System.exit(0);
        });

        // Run network discovery and connection on a separate background thread
        // to prevent freezing the JavaFX UI during the timeout period
        new Thread(() -> {
            networkClient = ServerDiscovery.establishConnection(properties);

            // Once the connection process completes (success or fail),
            // return execution to the main UI thread to update the screen
            Platform.runLater(() -> {
                try {
                    init();
                    // Switch to login UI
                    Scene sceneLogin = new Scene(rootLogin);
                    stage.setTitle("N7 Auction System - Client");
                    stage.setScene(sceneLogin);

                    log.info("Application started successfully.");

                    // Warn the user if the app had to fallback to offline/localhost mode
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