package gui;

import client.network.NetworkClient;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private String[] getServerAddress(String binId) {
        try {
            URL url = new URL("https://api.jsonbin.io/v3/b/" + binId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Bin-Meta", "false");
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setRequestProperty("Pragma", "no-cache");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder content = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) content.append(inputLine);
            in.close();

            String jsonResponse = content.toString().trim();
            String ip = "";
            String port = "";

            Matcher ipMatcher = Pattern.compile("\"ip\"\\s*:\\s*\"([^\"]+)\"").matcher(jsonResponse);
            if (ipMatcher.find()) ip = ipMatcher.group(1);

            Matcher portMatcher = Pattern.compile("\"port\"\\s*:\\s*(\\d+)").matcher(jsonResponse);
            if (portMatcher.find()) port = portMatcher.group(1);

            if (!ip.isEmpty() && !port.isEmpty()) {
                return new String[]{ip, port};
            } else {
                System.out.println("[Error]: " + RED + "JSONBin format error: " + jsonResponse + RESET);
                System.out.println(YELLOW + "Hint: Check your JSONBin data structure." + RESET);
            }
        } catch (Exception e) {
            System.out.println("[Error]: API Data Retrieval failed: " + RED + e.getMessage() + RESET);
        }
        return null;
    }

    public void openClient() {
        String binID = properties.getProperty("binID", "69d4960b856a6821890813a2");
        System.out.println("[System]: Fetching server address from remote storage...");

        String[] serverInfo = getServerAddress(binID);
        String serverURL;
        int port;

        if (serverInfo != null && serverInfo.length == 2) {
            serverURL = serverInfo[0];
            port = Integer.parseInt(serverInfo[1]);
            System.out.println("[System]: " + GREEN + "Successfully retrieved server address" + RESET);
        } else {
            System.out.println("[System]: " + BLUE + "Could not get remote address. Switching to Localhost (Fallback)" + RESET);
            serverURL = properties.getProperty("fallbackServerURL", "localhost");
            port = Integer.parseInt(properties.getProperty("fallbackServerPort", "6969"));
        }

        System.out.println("[System]: Connecting to: " + YELLOW + serverURL + ":" + port + RESET);
        networkClient = new NetworkClient(serverURL, port);
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

        ComboBox<String> registerRole = (ComboBox<String>) rootRegister.lookup("#registerRole");
        if (registerRole != null) {
            registerRole.getItems().clear();
            registerRole.getItems().addAll("Bidder", "Seller");
            registerRole.getSelectionModel().selectFirst();
        }
    }

    @Override
    public void start(Stage stage) throws IOException {
        // 1. Khởi tạo cấu hình
        initProperties();
        //openClient();
        init();

        primalStage = stage;
        Scene sceneLogin = new Scene(rootLogin);
        stage.setTitle("N7 Auction System - Client");
        stage.setScene(sceneLogin);
        stage.show();

        System.out.println("[Log]: " + GREEN + "Application started successfully" + RESET);
    }
}
