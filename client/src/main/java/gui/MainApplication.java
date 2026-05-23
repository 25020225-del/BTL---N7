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

import static utils.ConsoleColors.GREEN;
import static utils.ConsoleColors.RESET;

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

    private Properties properties = new Properties();

    /**
     * The standard Java main method.
     * It prints a startup banner to the console and launches the JavaFX application lifecycle.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {

        System.out.println(GREEN + "===================================================================");
        System.out.println("|                                                                 |");
        System.out.println("|                               LOG                               |");
        System.out.println("|                                                                 |");
        System.out.println("===================================================================" + RESET);
        launch(args);
    }

    /**
     * Dynamically swaps the walletView node of the primary active scene.
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
            registerCtrl.setNetworkClient(NetworkService.get());
        }

        LoginController loginCtrl = fxmlLogin.getController();
        if (loginCtrl != null) loginCtrl.setNetworkClient(NetworkService.get());
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

        // 1. TẠO MÀN HÌNH CHỜ TỪ CLASS ĐÃ TÁCH SẠCH SẼ
        SplashScreen splashScreen = new SplashScreen();

        // 2. HIỆN CỬA SỔ BAN ĐẦU VỚI MÀN HÌNH CHỜ
        Scene loadingScene = new Scene(splashScreen, 800, 600);
        stage.setTitle("N7 Auction System");
        stage.setScene(loadingScene);
        stage.show();

        primalStage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });

        // 3. KÍCH HOẠT CHẠY TOÀN BỘ HIỆU ỨNG (Vòng xoay + Chữ hiện dần)
        splashScreen.play();

        // ── 4. BACKGROUND THREAD KẾT NỐI (GIỮ NGUYÊN 100% LOGIC CŨ CỦA BẠN) ─────────────────
        new Thread(() -> {
            log.info("Attempting to connect to server...");
            client.network.NetworkService.set(ServerDiscovery.establishConnection(properties));

            Platform.runLater(() -> {
                try {
                    splashScreen.stopRotation(); // Dừng hiệu ứng xoay ở màn hình chờ tách riêng
                    init();
                    log.info("Handshake successful. Navigating to Login screen.");
                    Scene sceneLogin = new Scene(rootLogin);
                    stage.setTitle("N7 Auction System - Client");
                    stage.setScene(sceneLogin);

                    if (!NetworkService.get().isConnected()) {
                        log.warn("Server unreachable. Switched to OFFLINE mode.");
                        AlertUtils.showWarning("Network Issue", "Cannot connect to server. Running offline version");
                    } else {
                        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                            if (NetworkService.get() != null && NetworkService.get().isConnected()) {
                                NetworkService.get().sendMessage("TIME_SYNC", System.currentTimeMillis());
                            }
                        }, 0, 5, TimeUnit.SECONDS);
                        log.info("Connection established: " + NetworkService.get().getServerAddress());
                    }
                } catch (IOException e) {
                    log.error("Critical error during UI initialization: ", e);
                }
            });
        }).start();
    }

}