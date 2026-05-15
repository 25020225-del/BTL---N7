package server;

import controller.AuctionMonitor;
import controller.UserController;
import io.github.cdimascio.dotenv.Dotenv;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static utils.ConsoleColors.RESET;
import static utils.ConsoleColors.YELLOW;

/**
 * The core entry point for the N7 Auction System Server.
 * <p>
 * This class serves as the heart of the backend, handling several critical roles:
 * <ul>
 *     <li>Initializing the SQLite database alongside HikariCP for connection pooling.</li>
 *     <li>Automatically retrieving the public IP/Port from Localtonet and syncing it to JSONBin.</li>
 *     <li>Launching the background {@link controller.AuctionMonitor} to handle expired auctions.</li>
 *     <li>Instantiating and starting the high-performance NIO WebSocket server.</li>
 *     <li>Providing an interactive Command Line Interface (CLI) for direct server administration.</li>
 * </ul>
 */
public class MultiThreadedServer {
    private static final Logger log = LoggerFactory.getLogger(MultiThreadedServer.class);

    // Scheduler for periodic IP/Port updates
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Load environment variables
    private static final Dotenv dotenv = Dotenv.load();
    private static final String BIN_ID = dotenv.get("BIN_ID");
    private static final String JSONBIN_KEY = dotenv.get("JSONBIN_API_KEY");
    private static final String LOCALTONET_TOKEN = dotenv.get("LOCALTONET_API_TOKEN");

    // Cached address to prevent redundant API calls
    private static String lastSyncedIp = "";
    private static int lastSyncedPort = -1;

    // Core Dependencies (Initialized in main)
    private static UserController userController;
    private static server.handler.CommandDispatcher commandDispatcher;

    // === API SYNCING METHODS ===

    /**
     * Updates the server's current public IP and Port to JSONBin via HTTP PUT.
     * <p>
     * This method acts as a lightweight Dynamic DNS (DDNS) solution. By updating the JSONBin,
     * clients can always fetch the latest server address without hardcoded configurations.
     * It disables versioning ("X-Bin-Versioning") to save cloud storage quota.
     *
     * @param currentIp   The current public IP address or domain name.
     * @param currentPort The current public port.
     */
    public static void updateAddress(String currentIp, int currentPort) {
        try {
            String urlString = "https://api.jsonbin.io/v3/b/" + BIN_ID;
            URL url = new URL(urlString);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // Disable versioning to overwrite the exact same bin and save storage quota
            conn.setRequestProperty("X-Bin-Versioning", "false");

            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Master-Key", JSONBIN_KEY);
            conn.setDoOutput(true);

            // Manually construct JSON string to avoid loading heavy JSON libraries just for 2 variables
            String jsonInputString = "{\"ip\": \"" + currentIp + "\", \"port\": " + currentPort + "}";

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                log.debug("New IP - Port (JSON-Bin) synced: {}:{}", currentIp, currentPort);
            } else {
                log.error("" + responseCode);
            }
        } catch (Exception e) {
            log.error("Connection error: {}", e.getMessage());
        }
    }

    /**
     * Retrieves the current public IP and Port from the Localtonet API.
     * <p>
     * This method utilizes Regular Expressions (Regex) instead of heavy JSON mapping libraries
     * (like Jackson) to extract data quickly and keep the memory footprint extremely low.
     *
     * @return A String array where index 0 is the Domain/IP and index 1 is the Port,
     * or {@code null} if the tunnel is offline or an error occurs.
     */
    private static String[] getAddress() {
        try {
            URL url = new URL("https://localtonet.com/api/GetTunnels");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + LOCALTONET_TOKEN);
            conn.setRequestProperty("Accept", "application/json");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) content.append(inputLine);
            in.close();

            String jsonResponse = content.toString();

            // Extract connection status using Regex for lightweight parsing
            Matcher tunnelStatus = Pattern.compile("\"status\":(\\d+)").matcher(jsonResponse);
            if (tunnelStatus.find()) {
                // Status 0 means the Localtonet tunnel is currently offline/disabled
                if (Integer.parseInt(tunnelStatus.group(1)) == 0) return null;
            }

            String ip = "";
            String port = "";

            // Extract IP and Port via Regex to bypass strict JSON mapping
            Matcher ipMatcher = Pattern.compile("\"serverDomain\":\"([^\"]+)\"").matcher(jsonResponse);
            if (ipMatcher.find()) ip = ipMatcher.group(1);

            Matcher portMatcher = Pattern.compile("\"serverPort\":(\\d+)").matcher(jsonResponse);
            if (portMatcher.find()) port = portMatcher.group(1);

            if (!ip.isEmpty() && !port.isEmpty()) return new String[]{ip, port};
        } catch (Exception e) {
            log.error("Localtonet API Error: {}", e.getMessage());
        }
        return null;
    }

    // === WEBSOCKET SERVER CORE ===

    /**
     * The core WebSocket Server implementation utilizing Java-WebSocket.
     * <p>
     * It uses Non-blocking I/O (NIO) to handle thousands of concurrent client connections
     * efficiently without requiring a dedicated thread for each client.
     */
    private static class AuctionWSServer extends WebSocketServer {

        public AuctionWSServer(int port) {
            super(new InetSocketAddress(port));
            // [ARCHITECT FIX]: Bật cơ chế Heartbeat (Ping/Pong)
            // Server sẽ gửi gói Ping đến toàn bộ client định kỳ. Nếu một client rớt mạng đột ngột
            // và không phản hồi Pong trong 30 giây, kết nối ma (Ghost Connection) sẽ bị ép hủy bỏ.
            // Điều này kích hoạt ngay lập tức sự kiện onClose() để dọn dẹp ClientManager.
            this.setConnectionLostTimeout(30);
        }

        /**
         * Triggered automatically when a new client establishes a WebSocket connection.
         * <p>
         * It creates a new {@link ClientHandler}, attaches it to the WebSocket session,
         * and initiates the RSA security handshake.
         */
        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            log.info("New client connected from: {}", conn.getRemoteSocketAddress().getAddress().getHostAddress());

            // Initialize ClientHandler with the new WebSocket connection and global controllers/dispatcher
            ClientHandler clientHandler = new ClientHandler(conn, userController, commandDispatcher);

            // Attach ClientHandler to the connection for later retrieval
            conn.setAttachment(clientHandler);
            ClientManager.addClient(clientHandler);

            // Initiate security handshake by sending RSA Public Key
            clientHandler.startHandshake();
        }

        /**
         * Triggered when the WebSocket connection is closed (intentionally or due to an error).
         * This safely removes the client from the server's memory.
         */
        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            ClientHandler clientHandler = conn.getAttachment();
            if (clientHandler != null) {
                // Trigger connection cleanup
                clientHandler.closeConnection();
            }
        }

        /**
         * Triggered when the server receives a text frame from the client.
         * The payload is passed directly to the attached {@link ClientHandler} for AES decryption and dispatching.
         */
        @Override
        public void onMessage(WebSocket conn, String message) {
            ClientHandler clientHandler = conn.getAttachment();
            if (clientHandler != null) {
                // Pass incoming payload to ClientHandler for decryption and dispatching
                clientHandler.processIncomingMessage(message);
            }
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            log.error("WebSocket Error: {}", ex.getMessage());
        }

        @Override
        public void onStart() {
            log.info("Server is running on port {}", getPort());
        }
    }

    /**
     * The main execution block of the Server application.
     */
    public static void main(String[] args) {
        final int PORT = 6969;

        scheduler.scheduleAtFixedRate(() -> {
            try {
                String[] publicAddress = getAddress();
                if (publicAddress != null) {
                    String newIp = publicAddress[0];
                    int newPort = Integer.parseInt(publicAddress[1]);

                    if (!newIp.equals(lastSyncedIp) || newPort != lastSyncedPort) {
                        updateAddress(newIp, newPort);
                        lastSyncedIp = newIp;
                        lastSyncedPort = newPort;
                    }
                }
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }, 0, 30, TimeUnit.SECONDS);

        log.info("Getting address...");
        String[] publicAddress = getAddress();

        if (publicAddress != null) {
            updateAddress(publicAddress[0], Integer.parseInt(publicAddress[1]));
        } else {
            log.warn("Cannot get public address. Using localhost");
            updateAddress("127.0.0.1", PORT);
        }

        database.DatabaseManager.initializeDatabase();

        // 1. Initialize DAOs and Services
        database.dao.UserDAO userDAO = new database.dao.UserDAO();
        database.dao.AuctionDAO auctionDAO = new database.dao.AuctionDAO();
        database.dao.BidDAO bidDAO = new database.dao.BidDAO();
        database.dao.WalletDAO walletDAO = new database.dao.WalletDAO();
        service.TOTPService totpService = new service.TOTPService();

        // 2. Initialize Controllers with DI
        userController = new UserController(userDAO, totpService);
        controller.ServerSellerController sellerCtrl = new controller.ServerSellerController(auctionDAO);
        controller.ServerPaymentController paymentCtrl = new controller.ServerPaymentController(walletDAO);
        controller.ServerBidderController bidderCtrl = new controller.ServerBidderController(bidDAO);

        // 3. Inject dependencies into static utility services
        service.AutoBidEngine.setBidderController(bidderCtrl);

        // 4. Initialize Command Dispatcher with all required dependencies
        commandDispatcher = new server.handler.CommandDispatcher(
                userDAO, auctionDAO, bidDAO, walletDAO,
                totpService, sellerCtrl, paymentCtrl
        );

        // 4. Start background monitoring with injected DAOs
        AuctionMonitor monitor = new AuctionMonitor(AuctionManager.getAuctionList(), auctionDAO, walletDAO);
        monitor.startMonitoring();

        // RUN SERVER
        AuctionWSServer wsServer = new AuctionWSServer(PORT);
        wsServer.start();

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ClientManager.broadcast(YELLOW + "[System]: Server is shutting down. Every connecting client will be disconnected shortly." + RESET, null);
            log.info("Server has been shutdown.");
            monitor.stopMonitoring();
            scheduler.shutdown();
            ClientManager.shutdown();
            try {
                wsServer.stop();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));

        // === SERVER CONSOLE COMMANDS ===
        Scanner scanner = new Scanner(System.in);
        while (true) {
            if (scanner.hasNextLine()) {
                String serverMessage = scanner.nextLine();
                if (serverMessage.startsWith("/kick ")) {
                    String target = serverMessage.substring(6);
                    System.out.print("Reason: ");
                    String reason = scanner.nextLine();
                    ClientManager.kickTarget(target, reason);
                    continue;
                }
                if (serverMessage.startsWith("/kickn ")) {
                    try {
                        int index = Integer.parseInt(serverMessage.substring(7));
                        System.out.print("Reason: ");
                        String reason = scanner.nextLine();
                        ClientManager.kickTargetByNumber(index, reason);
                    } catch (NumberFormatException e) {
                        log.error("/kickn must be followed by an integer.");
                    }
                }
                if (serverMessage.startsWith("/msg ")) {
                    String data = serverMessage.substring(5);
                    int firstIndexOfSpace = data.indexOf(" ");
                    String receiver = data.substring(0, firstIndexOfSpace);
                    String message = data.substring(firstIndexOfSpace + 1);
                    ClientManager.privateMsg(receiver, message);
                    continue;
                }
                if (serverMessage.startsWith("/clist")) {
                    ClientManager.getClientList();
                    continue;
                }
                if (serverMessage.startsWith("/redirect ")) {
                    String[] data = serverMessage.substring(10).split(" ");
                    if (data.length == 2) {
                        ClientManager.redirectClient(data[0], data[1]);
                    }
                    continue;
                }
                ClientManager.broadcast("[Admin]: " + serverMessage, null);
            }
        }
    }
}