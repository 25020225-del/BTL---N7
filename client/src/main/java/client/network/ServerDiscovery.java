package client.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static utils.ConsoleColors.*;

/**
 * Utility class responsible for discovering the server's public address and
 * establishing the appropriate network connection.
 */
public class ServerDiscovery {
    private static final Logger log = LoggerFactory.getLogger(ServerDiscovery.class);

    /**
     * Fetches the server's dynamic IP and Port from a remote JSONBin storage.
     *
     * @param binId The JSONBin ID containing the server address.
     * @return A String array containing the IP at index 0 and Port at index 1, or null if failed.
     */
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
            log.error("API Data Retrieval failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Constructs the correct WebSocket URL based on the environment and establishes a connection.
     * Automatically enforces WSS (WebSocket Secure) for remote connections.
     *
     * @param properties Application properties containing fallback values and API keys.
     * @return An initialized NetworkClient instance.
     */
    public static NetworkClient establishConnection(Properties properties) {
        String binID = properties.getProperty("binID");
        log.info("Fetching server address...");

        String[] serverInfo = getServerAddress(binID);
        String serverURL;
        int port;

        if (serverInfo != null && serverInfo.length == 2) {
            serverURL = serverInfo[0];
            port = Integer.parseInt(serverInfo[1]);
            log.info("Successfully retrieved server address.");
        } else {
            log.warn("Could not get remote address. Switching to Localhost");
            serverURL = properties.getProperty("fallbackServerURL", "localhost");
            port = Integer.parseInt(properties.getProperty("fallbackServerPort", "6969"));
        }

        log.debug("Target server address: {}:{}", serverURL, port);

        // SECURE CONNECTION ROUTING:
        // Automatically determine if the connection is local or remote.
        boolean isLocal = serverURL.equals("localhost") || serverURL.equals("127.0.0.1");

        // Enforce secure WebSocket (wss://) for any external domain/IP.
        String protocol = isLocal ? "ws://" : "wss://";
        String fullUrl = protocol + serverURL + ":" + port;

        NetworkClient client = new NetworkClient(fullUrl);

        // Fallback mechanism if the main remote connection fails
        if (!client.isConnected() && !isLocal) {
            log.warn("Online server is unreachable. Automatically falling back to localhost...");

            String fallbackURL = properties.getProperty("fallbackServerURL", "localhost");
            int fallbackPort = Integer.parseInt(properties.getProperty("fallbackServerPort", "6969"));

            // Local fallback uses standard ws://
            String fallbackFullUrl = "ws://" + fallbackURL + ":" + fallbackPort;
            log.info("Connecting to fallback: {}", fallbackFullUrl);

            client = new NetworkClient(fallbackFullUrl);
        }

        if (!client.isConnected()) {
            log.warn(RED + "All connection attempts failed." + RESET);
            log.info(BLUE + "Opening offline application..." + RESET);
        }

        return client;
    }
}