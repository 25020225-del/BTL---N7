package gui;

import client.NetworkClient;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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

public class MainApplication extends Application {
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_GREEN = "\u001B[32m";

    public static Stage primalStage;
    public static Parent rootLogin;
    public static Parent rootRegister;
    public static Parent rootMainView;

    private Properties properties = new Properties();

    // Network manager instance
    public static NetworkClient networkClient;

    public static void main(String[] args){
        System.out.println(ANSI_GREEN+"=================================");
        System.out.println("|                               |");
        System.out.println("|       CLIENT LOG TABLE        |");
        System.out.println("|                               |");
        System.out.println("================================="+ANSI_RESET);
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
            System.out.println("[System]: Reading config");
            properties.load(input);
        } else {
            System.out.println("[Error]: Cannot find config.properties");
        }
    }

    private String[] getServerAddress(String binId) {
        try {
            URL url = new URL("https://api.jsonbin.io/v3/b/"+binId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Bin-Meta", "false");

            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setRequestProperty("Pragma", "no-cache");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder content = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();

            String jsonResponse = content.toString().trim();
            String ip = "";
            String port = "";

            Matcher ipMatcher = Pattern.compile("\"ip\"\\s*:\\s*\"([^\"]+)\"").matcher(jsonResponse);
            if (ipMatcher.find()) ip = ipMatcher.group(1);

            Matcher portMatcher = Pattern.compile("\"port\"\\s*:\\s*(\\d+)").matcher(jsonResponse);
            if (portMatcher.find()) port = portMatcher.group(1);

            if (!ip.isEmpty() && !port.isEmpty()) return new String[]{ip, port};
            else {
                System.out.println("[Error]: JSONBin Error: " + jsonResponse
                                  +"\nUse the above for debugging");
            }
        } catch (Exception e) {
            System.out.println("[Error]: API Data Error: " + e.getMessage());
        }
        return null;
    }

    //Creating connection to server
    public void openClient() {
        String binID = properties.getProperty("binID", "69d4960b856a6821890813a2");
        System.out.println("[System]: Getting server address");

        String[] serverInfo = getServerAddress(binID);
        String serverURL;
        int port;

        // Getting address from JSONBin
        if (serverInfo != null && serverInfo.length == 2) {
            serverURL = serverInfo[0];
            port = Integer.parseInt(serverInfo[1]);
            System.out.println("[System]: Successfuly got server address");
        }
        // Localhost for contingency
        else {
            System.out.println("[Error]: Cannot get serveraddress. Switched to localhost (Fallback)");
            serverURL = properties.getProperty("fallbackServerURL", "localhost");
            port = Integer.parseInt(properties.getProperty("fallbackServerPort", "6969"));
        }

        System.out.println("[System]: Connecting to: " + serverURL + ":" + port);
        networkClient = new NetworkClient(serverURL, port);
    }

    public void init() throws IOException {
        FXMLLoader fxmlLogin = new FXMLLoader(MainApplication.class.getResource("Login.fxml"));
        FXMLLoader fxmlRegister = new FXMLLoader(MainApplication.class.getResource("Register.fxml"));
        FXMLLoader fxmlMainView = new FXMLLoader(MainApplication.class.getResource("MainView.fxml"));

        rootLogin = fxmlLogin.load();
        rootRegister = fxmlRegister.load();
        rootMainView = fxmlMainView.load();

        RegisterController registerCtrl = fxmlRegister.getController();
        if (registerCtrl != null) {
            registerCtrl.setNetworkClient(networkClient);
        }

        LoginController loginCtrl = fxmlLogin.getController();
        if (loginCtrl != null) {
            loginCtrl.setNetworkClient(networkClient);
        }
    }

    @Override
    public void start(Stage stage) throws IOException {
        initProperties();
        openClient();
        init();

        primalStage = stage;
        Scene sceneLogin = new Scene(rootLogin);
        stage.setScene(sceneLogin);
        stage.show();
    }
}