package gui;

import gui.process.AlertHelper;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.util.Duration;
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

        // ── 1. ROOT CONTAINER ──────────────────────────────────────────
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: #1a1a2e;");

        VBox centerBox = new VBox(0);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setMaxWidth(320);
        StackPane.setAlignment(centerBox, Pos.CENTER);

        // ── 2. LOGO (chữ N7 + spinning arc) ───────────────────────────
        StackPane logoPane = new StackPane();
        logoPane.setPrefSize(90, 90);
        logoPane.setMaxSize(90, 90);

        // Nền vuông bo góc
        Rectangle logoRect = new Rectangle(64, 64);
        logoRect.setArcWidth(20);
        logoRect.setArcHeight(20);
        logoRect.setFill(Color.web("#4f46e5"));
        logoRect.setOpacity(0);

        // Chữ N7
        Label logoLabel = new Label("N7");
        logoLabel.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: white;");
        logoLabel.setOpacity(0);

        // Arc xoay
        Arc spinArc = new Arc(45, 45, 40, 40, 90, 80);
        spinArc.setType(ArcType.OPEN);
        spinArc.setFill(Color.TRANSPARENT);
        spinArc.setStroke(Color.web("#818cf8"));
        spinArc.setStrokeWidth(2.5);
        spinArc.setStrokeLineCap(StrokeLineCap.ROUND);

        // Vòng tròn nền mờ
        Arc bgArc = new Arc(45, 45, 40, 40, 0, 360);
        bgArc.setType(ArcType.OPEN);
        bgArc.setFill(Color.TRANSPARENT);
        bgArc.setStroke(Color.web("#4f46e5"));
        bgArc.setStrokeWidth(1.5);
        bgArc.setOpacity(0.2);

        logoPane.getChildren().addAll(bgArc, logoRect, logoLabel, spinArc);

        // Animation xoay arc
        RotateTransition rotate = new RotateTransition(Duration.millis(1200), spinArc);
        rotate.setByAngle(360);
        rotate.setCycleCount(Animation.INDEFINITE);
        rotate.setInterpolator(Interpolator.LINEAR);
        rotate.play();

        VBox.setMargin(logoPane, new Insets(0, 0, 24, 0));

        // ── 3. TITLE ──────────────────────────────────────────────────
        Label titleLabel = new Label("N7 Auction System");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: 500; -fx-text-fill: #e2e8f0;");
        titleLabel.setOpacity(0);
        VBox.setMargin(titleLabel, new Insets(0, 0, 20, 0));

        // ── 4. STEPS ──────────────────────────────────────────────────
        String[][] stepDefs = {
                {"Fetching server address...",     "PENDING"},
                {"Establishing secure channel...", "PENDING"},
                {"RSA handshake...",               "PENDING"},
                {"Loading marketplace...",         "PENDING"},
        };

        Circle[]    stepDots   = new Circle[stepDefs.length];
        Label[]     stepLabels = new Label[stepDefs.length];
        HBox[]      stepRows   = new HBox[stepDefs.length];

        VBox stepsBox = new VBox(10);
        stepsBox.setAlignment(Pos.CENTER_LEFT);
        stepsBox.setMaxWidth(220);

        for (int i = 0; i < stepDefs.length; i++) {
            Circle dot = new Circle(4);
            dot.setFill(Color.web("#374151"));
            stepDots[i] = dot;

            Label lbl = new Label(stepDefs[i][0]);
            lbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8;");
            stepLabels[i] = lbl;

            HBox row = new HBox(10, dot, lbl);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setOpacity(0);
            stepRows[i] = row;
            stepsBox.getChildren().add(row);
        }

        VBox.setMargin(stepsBox, new Insets(0, 0, 16, 0));

        // ── 5. VERSION ────────────────────────────────────────────────
        Label versionLabel = new Label("v1.0.0  ·  N7 Group Project");
        versionLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");
        versionLabel.setOpacity(0);

        centerBox.getChildren().addAll(logoPane, titleLabel, stepsBox, versionLabel);
        root.getChildren().add(centerBox);

        // ── 6. HIỆN CỬA SỔ ───────────────────────────────────────────
        Scene loadingScene = new Scene(root, 800, 600);
        stage.setTitle("N7 Auction System");
        stage.setScene(loadingScene);
        stage.show();

        primalStage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });

        // ── 7. FADE-IN ANIMATION SEQUENCE ─────────────────────────────
        // Logo rect
        FadeTransition fadeRect = new FadeTransition(Duration.millis(400), logoRect);
        fadeRect.setToValue(1);

        // Logo chữ
        FadeTransition fadeLogo = new FadeTransition(Duration.millis(400), logoLabel);
        fadeLogo.setToValue(1);

        // Title
        FadeTransition fadeTitle = new FadeTransition(Duration.millis(500), titleLabel);
        fadeTitle.setToValue(1);

        // Steps hiện dần, mỗi cái cách nhau 600ms
        SequentialTransition stepsAnim = new SequentialTransition();
        for (int i = 0; i < stepRows.length; i++) {
            PauseTransition pause = new PauseTransition(Duration.millis(i == 0 ? 0 : 600));
            FadeTransition fadeRow = new FadeTransition(Duration.millis(400), stepRows[i]);
            fadeRow.setToValue(1);
            final int idx = i;
            fadeRow.setOnFinished(e -> stepDots[idx].setFill(Color.web("#818cf8")));
            stepsAnim.getChildren().addAll(pause, fadeRow);
        }

        // Version
        FadeTransition fadeVer = new FadeTransition(Duration.millis(400), versionLabel);
        fadeVer.setToValue(1);

        // Chạy toàn bộ sequence
        SequentialTransition fullSequence = new SequentialTransition(
                new PauseTransition(Duration.millis(200)),  fadeRect,
                new PauseTransition(Duration.millis(100)),  fadeLogo,
                new PauseTransition(Duration.millis(200)),  fadeTitle,
                stepsAnim,
                fadeVer
        );
        fullSequence.play();

        // ── 8. BACKGROUND THREAD KẾT NỐI ─────────────────────────────
        new Thread(() -> {
            log.info("Attempting to connect to server...");
            networkClient = ServerDiscovery.establishConnection(properties);

            Platform.runLater(() -> {
                try {
                    rotate.stop(); // dừng animation xoay
                    init();
                    log.info("Handshake successful. Navigating to Login screen.");
                    Scene sceneLogin = new Scene(rootLogin);
                    stage.setTitle("N7 Auction System - Client");
                    stage.setScene(sceneLogin);

                    if (!networkClient.isConnected()) {
                        log.warn("Server unreachable. Switched to OFFLINE mode.");
                        AlertHelper.showAlert(Alert.AlertType.WARNING,
                                "Network Issue", "Cannot connect to server. Running offline version.");
                    }
                    else {
                        log.info("Connection established: " + networkClient.getServerAddress());
                    }
                } catch (IOException e) {
                    log.error("Critical error during UI initialization: ", e);
                }
            });
        }).start();
    }

}