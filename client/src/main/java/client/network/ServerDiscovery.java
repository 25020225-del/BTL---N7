package client.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static utils.ConsoleColors.*;

public class ServerDiscovery {

    private static String[] getServerAddress(String binId) {
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
            }
        } catch (Exception e) {
            System.out.println("[Error]:" + RED + " API Data Retrieval failed: " + e.getMessage() + RESET);
        }
        return null;
    }

    public static NetworkClient establishConnection(Properties properties) {
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
        NetworkClient client = new NetworkClient(serverURL, port);

        if (!client.isConnected() && !serverURL.equals("localhost") && !serverURL.equals("127.0.0.1")) {
            System.out.println("\n[System]:" + YELLOW + " Online server is unreachable. Automatically falling back to localhost..." + RESET);

            String fallbackURL = properties.getProperty("fallbackServerURL", "localhost");
            int fallbackPort = Integer.parseInt(properties.getProperty("fallbackServerPort", "6969"));

            System.out.println("[System]: Connecting to fallback: " + YELLOW + fallbackURL + ":" + fallbackPort + RESET);

            client = new NetworkClient(fallbackURL, fallbackPort);
        }

        if (!client.isConnected()) {
            System.out.println("\n[System]: " + RED + "All connection attempts failed." + RESET);
            System.out.println("[System]: " + BLUE + "Opening offline application..." + RESET);
        }

        return client;
    }
}