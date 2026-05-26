package server;

import controller.AuctionMonitor;
import controller.UserController;
import database.DatabaseManager;
import io.github.cdimascio.dotenv.Dotenv;
import model.auction.Auction;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static utils.ConsoleColors.RESET;
import static utils.ConsoleColors.YELLOW;

/**
 * Bootstrap entry-point for server.
 * Provisions data storage, configures dynamic address discovery, and activates engine daemons.
 */
public class MultiThreadedServer {
    private static final Logger log = LoggerFactory.getLogger(MultiThreadedServer.class);
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final Dotenv dotenv = Dotenv.load();
    private static final String BIN_ID = dotenv.get("BIN_ID");
    private static final String JSONBIN_KEY = dotenv.get("JSONBIN_API_KEY");
    private static final String LOCALTONET_TOKEN = dotenv.get("LOCALTONET_API_TOKEN");

    private static String lastSyncedIp = "";
    private static int lastSyncedPort = -1;
    private static UserController userController;
    private static server.handler.CommandDispatcher commandDispatcher;

    private static final int NETWORK_TIMEOUT_MS = 5000;

    public static boolean updateAddress(String currentIp, int currentPort) {
        try {
            HttpURLConnection conn = getJSONBinConnection();

            String jsonInputString = "{\"ip\": \"" + currentIp + "\", \"port\": " + currentPort + "}";
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                log.debug("New IP - Port synced: {}:{}", currentIp, currentPort);
                return true;
            } else {
                log.error("Unexpected HTTP response code {} from address sync endpoint", responseCode);
                return false;
            }
        } catch (Exception e) {
            log.error("Connection error: {}", e.getMessage());
            return false;
        }
    }

    private static HttpURLConnection getJSONBinConnection() throws IOException {
        String urlString = "https://api.jsonbin.io/v3/b/" + BIN_ID;
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setConnectTimeout(NETWORK_TIMEOUT_MS);
        conn.setReadTimeout(NETWORK_TIMEOUT_MS);

        conn.setRequestProperty("X-Bin-Versioning", "false");
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Master-Key", JSONBIN_KEY);
        conn.setDoOutput(true);
        return conn;
    }

    private static String[] getAddress() {
        try {
            String jsonResponse = getLocaltonetResponse();
            Matcher tunnelStatus = Pattern.compile("\"status\":(\\d+)").matcher(jsonResponse);
            if (tunnelStatus.find() && Integer.parseInt(tunnelStatus.group(1)) == 0) return null;

            String ip = "";
            String port = "";
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

    private static String getLocaltonetResponse() throws IOException {
        URL url = new URL("https://localtonet.com/api/GetTunnels");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setConnectTimeout(NETWORK_TIMEOUT_MS);
        conn.setReadTimeout(NETWORK_TIMEOUT_MS);

        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + LOCALTONET_TOKEN);
        conn.setRequestProperty("Accept", "application/json");

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) content.append(inputLine);
        in.close();

        return content.toString();
    }

    private static class AuctionWSServer extends WebSocketServer {

        public AuctionWSServer(int port) {
            super(new InetSocketAddress(port));
            this.setConnectionLostTimeout(30);
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            log.info("New client connected from: {}", conn.getRemoteSocketAddress().getAddress().getHostAddress());
            ClientHandler clientHandler = new ClientHandler(conn, userController, commandDispatcher);
            conn.setAttachment(clientHandler);
            ClientManager.addClient(clientHandler);
            clientHandler.startHandshake();
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            ClientHandler clientHandler = conn.getAttachment();
            if (clientHandler != null) {
                clientHandler.closeConnection();
            }
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            ClientHandler clientHandler = conn.getAttachment();
            if (clientHandler != null) {
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

    public static void main(String[] args) {
        final int PORT = 6969;

        database.DatabaseManager.initializeDatabase();
        database.dao.UserDAO userDAO = new database.dao.UserDAO();
        database.dao.AuctionDAO auctionDAO = new database.dao.AuctionDAO();
        database.dao.BidDAO bidDAO = new database.dao.BidDAO();
        database.dao.WalletDAO walletDAO = new database.dao.WalletDAO();
        database.dao.WithdrawalDAO withdrawalDAO = new database.dao.WithdrawalDAO();
        service.TOTPService totpService = new service.TOTPService();
        service.PasswordResetService passwordResetService = new service.PasswordResetService();

        userController = new UserController(userDAO, totpService, passwordResetService);
        controller.ServerSellerController sellerCtrl = new controller.ServerSellerController(auctionDAO);
        controller.ServerPaymentController paymentCtrl = new controller.ServerPaymentController(walletDAO, withdrawalDAO);
        controller.ServerBidderController bidderCtrl = new controller.ServerBidderController(bidDAO);

        service.AutoBidEngine.setBidderController(bidderCtrl);

        commandDispatcher = new server.handler.CommandDispatcher(
                userDAO,
                auctionDAO,
                bidDAO,
                walletDAO,
                withdrawalDAO,
                totpService,
                sellerCtrl,
                paymentCtrl,
                passwordResetService
        );

        try {
            log.info("Hydrating active auctions from database status parameters into RAM infrastructure...");
            List<Map<String, Object>> activeAuctionsFromDb = auctionDAO.getAuctionsByStatus("RUNNING", "OPEN", "WAITING_FOR_BID");
            for (Map<String, Object> map : activeAuctionsFromDb) {
                Auction auction = Auction.buildAuctionFromMap(map);
                AuctionManager.addAuctionToMonitor(auction);
            }
            log.info("Successfully hydrated {} active auction rooms into memory.", activeAuctionsFromDb.size());
        } catch (Exception e) {
            log.error("Fatal failure compiling database state recovery mappings: {}", e.getMessage());
        }

        AuctionMonitor monitor = new AuctionMonitor(AuctionManager.getAuctionList(), auctionDAO, walletDAO);
        monitor.startMonitoring();

        AuctionWSServer wsServer = new AuctionWSServer(PORT);
        wsServer.start();

        scheduler.scheduleAtFixedRate(() -> {
            try {
                log.info("Spawning transport initialization worker background sequence loop...");
                String[] publicAddress = getAddress();

                if (publicAddress != null) {
                    String newIp = publicAddress[0];
                    int newPort = Integer.parseInt(publicAddress[1]);

                    if (!newIp.equals(lastSyncedIp) || newPort != lastSyncedPort) {
                        if (updateAddress(newIp, newPort)) {
                            lastSyncedIp = newIp;
                            lastSyncedPort = newPort;
                        }
                    }
                } else if (lastSyncedIp.isEmpty()) {
                    log.warn("Cannot get public address. Using fallback localhost addressing scheme.");
                    if (updateAddress("127.0.0.1", PORT)) {
                        lastSyncedIp = "127.0.0.1";
                        lastSyncedPort = PORT;
                    }
                }
            } catch (Exception e) {
                log.error("Network initialization background error: {}", e.getMessage());
            }
        }, 0, 30, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ClientManager.broadcast(YELLOW + "[System]: Server is shutting down. Every connecting client will be disconnected shortly." + RESET, null);
            log.info("Server has been shutdown.");
            monitor.stopMonitoring();
            scheduler.shutdown();
            if (commandDispatcher != null) {
                commandDispatcher.shutdown();
            }
            ClientManager.shutdown();
            try {
                wsServer.stop();
            } catch (InterruptedException e) {
                log.error("Interrupted while stopping WebSocket server during shutdown", e);
                Thread.currentThread().interrupt();
            }
            DatabaseManager.closePool();
        }));

        Scanner scanner = new Scanner(System.in);
        try {
            while (true) {
                String serverMessage = scanner.nextLine();
                serverMessage = serverMessage.trim();

                if (serverMessage.isEmpty()) continue;

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
                    continue;
                }
                if (serverMessage.startsWith("/msg ")) {
                    String data = serverMessage.substring(5);
                    int firstIndexOfSpace = data.indexOf(" ");
                    if (firstIndexOfSpace != -1) {
                        String receiver = data.substring(0, firstIndexOfSpace);
                        String message = data.substring(firstIndexOfSpace + 1);
                        ClientManager.privateMsg(receiver, message);
                    }
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
        } catch (NoSuchElementException | IllegalStateException e) {
            log.info("Console input stream closed context safely. Keeping server thread bounds alive.");
            while (true) {
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            scanner.close();
        }
    }
}