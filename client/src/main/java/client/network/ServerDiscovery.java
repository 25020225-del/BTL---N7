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

/**
 * Utility orchestrator responsible for compiling structural configuration parameters,
 * resolving remote infrastructure discovery API targets, and selecting connection paths.
 */
public class ServerDiscovery {
    private static final Logger log = LoggerFactory.getLogger(ServerDiscovery.class);

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
            log.error("Infrastructure lookup from central directory failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Evaluates dynamic infrastructure lookup endpoints, resolves encryption routing protocols,
     * and executes structural initialization contracts for the communication layer.
     *
     * @param properties application configuration runtime parameters
     * @return an activated, authenticated network client wrapper context
     */
    public static NetworkClient establishConnection(Properties properties) {
        String binID = properties.getProperty("binID");
        log.info("Requesting remote directory resolution for endpoint target...");

        String[] serverInfo = getServerAddress(binID);
        String serverURL;
        int port;

        if (serverInfo != null && serverInfo.length == 2) {
            serverURL = serverInfo[0];
            port = Integer.parseInt(serverInfo[1]);
            log.info("Dynamic infrastructure verification completed successfully.");
        } else {
            log.warn("Remote address compilation failed. Reverting pipeline to fallback loop.");
            serverURL = properties.getProperty("fallbackServerURL", "localhost");
            port = Integer.parseInt(properties.getProperty("fallbackServerPort", "6969"));
        }

        log.debug("Target transport address resolved: {}:{}", serverURL, port);

        boolean isLocal = serverURL.equals("localhost") || serverURL.equals("127.0.0.1");
        String protocol = isLocal ? "ws://" : "wss://";
        String fullUrl = protocol + serverURL + ":" + port;

        NetworkClient client = new NetworkClient(fullUrl);
        client.connect();

        if (!client.isConnected() && !isLocal) {
            log.warn("Primary deployment endpoint is unreachable. Initiating secondary localhost fallback loop.");

            String fallbackURL = properties.getProperty("fallbackServerURL", "localhost");
            int fallbackPort = Integer.parseInt(properties.getProperty("fallbackServerPort", "6969"));
            String fallbackFullUrl = "ws://" + fallbackURL + ":" + fallbackPort;
            log.info("Binding transport to localized container: {}", fallbackFullUrl);

            client = new NetworkClient(fallbackFullUrl);
            client.connect();
        }

        if (!client.isConnected()) {
            log.error("Fatal: All localized and remote connection pipelines failed verification framework.");
            log.info("Initializing offline sandbox application state...");
        }

        return client;
    }
}