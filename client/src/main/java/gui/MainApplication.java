package gui;

import client.network.NetworkService;
import client.network.ServerDiscovery;
import gui.process.AlertUtils;
import gui.widget.SplashScreen;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * System root entry point orchestrating application orchestration, asset property resolution,
 * configuration stream bindings, and full-duplex asymmetric secure transport tunnel initialization.
 */
public class MainApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    public static Stage primalStage;
    public static Parent rootLogin;
    public static Parent rootRegister;

    private final Properties properties = new Properties();

    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Reconfigures the root visual element of the active primary window hierarchy.
     *
     * @param k the target structural layout node context to display
     */
    public static void setNewScene(Parent k) {
        if (k == null) {
            log.warn("Scene layout re-assignment aborted: targeted configuration is unassigned.");
            return;
        }
        if (primalStage != null && primalStage.getScene() != null) {
            primalStage.getScene().setRoot(k);
        }
    }

    /**
     * Extracts operational configurations from localized resource streams into dynamic runtime parameters.
     *
     * @throws IOException if external persistent file indicators cannot be accessed
     */
    public void initProperties() throws IOException {
        InputStream input = MainApplication.class.getResourceAsStream("config.properties");
        if (input != null) {
            log.info("Compiling system environmental properties file mapping layers...");
            properties.load(input);
        } else {
            log.error("Fatal initialization boundary configuration fail: config.properties resolution aborted.");
        }
    }

    /**
     * Compiles core immutable layout files and establishes references across transport subsystems.
     *
     * @throws IOException if local layout graph structural references are missing
     */
    public void init() throws IOException {
        FXMLLoader fxmlLogin = new FXMLLoader(MainApplication.class.getResource("Login.fxml"));
        FXMLLoader fxmlRegister = new FXMLLoader(MainApplication.class.getResource("Register.fxml"));

        rootLogin = fxmlLogin.load();
        rootRegister = fxmlRegister.load();

        RegisterController registerCtrl = fxmlRegister.getController();
        if (registerCtrl != null) {
            registerCtrl.setNetworkClient(NetworkService.get());
        }

        LoginController loginCtrl = fxmlLogin.getController();
        if (loginCtrl != null) {
            loginCtrl.setNetworkClient(NetworkService.get());
        }
    }

    @Override
    public void start(Stage stage) throws IOException {
        primalStage = stage;
        initProperties();

        SplashScreen splashScreen = new SplashScreen();
        Scene loadingScene = new Scene(splashScreen, 800, 600);

        stage.setTitle("N7 Auction System");
        stage.setScene(loadingScene);
        stage.show();

        primalStage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });

        splashScreen.play();

        new Thread(() -> {
            log.info("Spawning transport initialization worker background sequence loop thread...");
            NetworkService.set(ServerDiscovery.establishConnection(properties));

            Platform.runLater(() -> {
                try {
                    splashScreen.stopRotation();
                    init();

                    Scene sceneLogin = new Scene(rootLogin);
                    stage.setTitle("N7 Auction System - Client");
                    stage.setScene(sceneLogin);

                    if (!NetworkService.get().isConnected()) {
                        log.warn("Asymmetric network validation handshake skipped: transport client offline.");
                        AlertUtils.showWarning("Network Issue", "Cannot connect to server. Running offline version");
                    } else {
                        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                            if (NetworkService.get() != null && NetworkService.get().isConnected()) {
                                NetworkService.get().sendMessage("TIME_SYNC", System.currentTimeMillis());
                            }
                        }, 0, 5, TimeUnit.SECONDS);
                        log.info("Persistent encrypted handshake sync bound to target: {}", NetworkService.get().getServerAddress());
                    }
                } catch (IOException e) {
                    log.error("Fatal visual architecture compilation collapse during layout loading sequence", e);
                }
            });
        }).start();
    }
}