package server;

import controller.AuctionMonitor;
import controller.UserController;
import io.github.cdimascio.dotenv.Dotenv;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static utils.ConsoleColors.*;

public class MultiThreadedServer {

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static final Dotenv dotenv = Dotenv.load();
    private static final String BIN_ID = dotenv.get("BIN_ID");
    private static final String JSONBIN_KEY = dotenv.get("JSONBIN_API_KEY");
    private static final String LOCALTONET_TOKEN = dotenv.get("LOCALTONET_API_TOKEN");

    private static String lastSyncedIp = "";
    private static int lastSyncedPort = -1;

    private static final UserController userController = new UserController();

    public static void updateBulletinBoard(String currentIp, int currentPort) {
        try {
            String urlString = "https://api.jsonbin.io/v3/b/" + BIN_ID;
            URL url = new URL(urlString);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("X-Bin-Versioning", "false");

            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Master-Key", JSONBIN_KEY);
            conn.setDoOutput(true);

            String jsonInputString = "{\"ip\": \"" + currentIp + "\", \"port\": " + currentPort + "}";
            System.out.println("[Debug] JSON to JSONBin: " + jsonInputString);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                System.out.println("[JSONBin]: New IP - Port synced: " + YELLOW + currentIp + ":" + currentPort + RESET);
            } else {
                System.err.println("[JSONBin]: Error: " + RED + responseCode + RESET + " at URL: " + YELLOW + urlString + RESET);
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                    System.err.println("Error details: " + br.readLine());
                }
            }
        } catch (Exception e) {
            System.out.println("[JSONBin]: Connection Error: " + RED + e.getMessage() + RESET);
        }
    }

    private static String[] getLocaltonetAddress() {
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

            Matcher tunnelStatus = Pattern.compile("\"status\":(\\d+)").matcher(jsonResponse);
            if (tunnelStatus.find()) {
                int status = Integer.parseInt(tunnelStatus.group(1));
                if (status == 0) {
                    return null;
                }
            }

            String ip = "";
            String port = "";

            Matcher ipMatcher = Pattern.compile("\"serverDomain\":\"([^\"]+)\"").matcher(jsonResponse);
            if (ipMatcher.find()) ip = ipMatcher.group(1);

            Matcher portMatcher = Pattern.compile("\"serverPort\":(\\d+)").matcher(jsonResponse);
            if (portMatcher.find()) port = portMatcher.group(1);

            if (!ip.isEmpty() && !port.isEmpty()) return new String[]{ip, port};
        } catch (Exception e) {
            System.out.println("[System]: Localtonet API Error: " + RED + e.getMessage() + RESET);
        }
        return null;
    }

    public static void main(String[] args) {
        final int PORT = 6969;

        scheduler.scheduleAtFixedRate(() -> {
            try {
                String[] publicAddress = getLocaltonetAddress();
                if (publicAddress != null) {
                    String newIp = publicAddress[0];
                    int newPort = Integer.parseInt(publicAddress[1]);

                    if (!newIp.equals(lastSyncedIp) || newPort != lastSyncedPort) {
                        System.out.println(YELLOW + "\n[Auto-Sync]: Localtonet address change detected. Updating JSONBin..." + RESET);
                        updateBulletinBoard(newIp, newPort);
                        lastSyncedIp = newIp;
                        lastSyncedPort = newPort;
                        System.out.println(GREEN + "[Auto-Sync]: Successfully synced: " + YELLOW + newIp + ":" + newPort + RESET);
                    }
                } else {
                    System.out.println("[Auto-Sync]: Error: " + RED + "Cannot call API" + RESET);
                }
            } catch (Exception e) {
                System.err.println("[Auto-Sync]: System error: " + RED + e.getMessage() + RESET);
            }
        }, 0, 30, TimeUnit.SECONDS);

        System.out.println("[System]: Getting address...");
        String[] publicAddress = getLocaltonetAddress();

        if (publicAddress != null) {
            updateBulletinBoard(publicAddress[0], Integer.parseInt(publicAddress[1]));
        } else {
            System.out.println(BLUE + "[System]: Cannot get Localtonet address. Using localhost" + RESET);
            updateBulletinBoard("127.0.0.1", PORT);
        }

        database.DatabaseManager.initializeDatabase();

        AuctionMonitor monitor = new AuctionMonitor(AuctionManager.getAuctionList());
        monitor.startMonitoring();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ClientManager.broadcast(YELLOW + "[System]: Server is shutting down. Every connecting client will be disconnected shortly" + RESET, null);
            System.out.println(YELLOW + "[System]: Server has been shutdown" + RESET);
            monitor.stopMonitoring();
            scheduler.shutdown();
            ClientManager.shutdown();
        }));

        Thread serverChatThread = new Thread(() -> {
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
                    if (serverMessage.startsWith("/clist")) {
                        ClientManager.getClientList();
                        continue;
                    }
                    if (serverMessage.startsWith("/kickn ")) {
                        try {
                            String index = serverMessage.substring(7);
                            System.out.print("Reason: ");
                            String reason = scanner.nextLine();
                            ClientManager.kickTargetByNumber(Integer.parseInt(index), reason);
                        } catch (NumberFormatException e) {
                            System.out.println("[System]: Error: " + RED + "Index of /kickn command must be an integer" + RESET);
                        }
                        continue;
                    }
                    if (serverMessage.startsWith("/redirect ")) {
                        String[] data = serverMessage.substring(10).split(" ");
                        if (data.length == 2) {
                            ClientManager.redirectClient(data[0], data[1]);
                        } else {
                            System.out.println("[System]: Invalid format. Use /redirect <username> <url>");
                        }
                        continue;
                    }
                    if (serverMessage.startsWith("/msg ")) {
                        try {
                            int s = serverMessage.indexOf("\"");
                            int e = serverMessage.lastIndexOf("\"");
                            ClientManager.privateMsg(serverMessage.substring(5, s), serverMessage.substring(s + 1, e));
                        } catch (StringIndexOutOfBoundsException e) {
                            System.out.println("[System]: Invalid command format");
                        }
                        continue;
                    }
                    ClientManager.broadcast("[Admin]: " + serverMessage, null);
                }
            }
        });
        serverChatThread.start();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[System]: Server is running on port " + YELLOW + PORT + RESET);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[System]: New client connected from: " + YELLOW + socket.getInetAddress().getHostAddress() + RESET);

                ClientHandler clientHandler = new ClientHandler(socket, userController);

                ClientManager.addClient(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.out.println("[System]: Server Error: " + RED + e.getMessage() + RESET);
        }
    }
}