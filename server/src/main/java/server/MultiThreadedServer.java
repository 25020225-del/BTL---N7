package server;

import controller.AuctionMonitor;
import controller.UserController;
import io.github.cdimascio.dotenv.Dotenv;
import model.Auction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static utils.ConsoleColors.*;

public class MultiThreadedServer {

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static final String BIN_ID = "69d4960b856a6821890813a2";
    private static final Dotenv dotenv = Dotenv.load();
    private static final String JSONBIN_KEY = dotenv.get("JSONBIN_API_KEY");
    private static final String LOCALTONET_TOKEN = dotenv.get("LOCALTONET_API_TOKEN");

    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private static final ExecutorService broadcastPool = Executors.newFixedThreadPool(20);

    private static String lastSyncedIp = "";
    private static int lastSyncedPort = -1;

    private static final UserController userController = new UserController();

    public static final List<Auction> auctionList = new ArrayList<>();

    public static void updateBulletinBoard(String currentIp, int currentPort) {
        try {
            String urlString = "https://api.jsonbin.io/v3/b/" + BIN_ID;
            URL url = new URL(urlString);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Master-Key", JSONBIN_KEY);
            conn.setDoOutput(true);

            String jsonInputString = "{\"ip\": \"" + currentIp + "\", \"port\": " + currentPort + "}";

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

        AuctionMonitor monitor = new AuctionMonitor(auctionList);
        monitor.startMonitoring();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            broadcast(YELLOW + "[System]: Server is shutting down. Every connecting client will be disconnected shortly" + RESET, null);
            System.out.println(YELLOW + "[System]: Server has been shutdown" + RESET);
            monitor.stopMonitoring();
            scheduler.shutdown();
            broadcastPool.shutdown();
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
                        kickTarget(target, reason);
                        continue;
                    }
                    if (serverMessage.startsWith("/clist")) {
                        getClientList();
                        continue;
                    }
                    if (serverMessage.startsWith("/kickn ")) {
                        try {
                            String index = serverMessage.substring(7);
                            System.out.print("Reason: ");
                            String reason = scanner.nextLine();
                            kickTargetByNumber(Integer.parseInt(index), reason);
                        } catch (NumberFormatException e) {
                            System.out.println("[System]: Error: " + RED + "Index of /kickn command must be an integer" + RESET);
                        }
                        continue;
                    }
                    if (serverMessage.startsWith("/redirect ")) {
                        String[] data = serverMessage.substring(10).split(" ");
                        for (ClientHandler client : clients) {
                            if (client.getClientName().equals(data[0])) {
                                client.redirectToWebsite(data[1]);
                            }
                        }
                        continue;
                    }
                    if (serverMessage.startsWith("/msg ")) {
                        try {
                            int s = serverMessage.indexOf("\"");
                            int e = serverMessage.lastIndexOf("\"");
                            privateMsg(serverMessage.substring(5, s), serverMessage.substring(s + 1, e));
                        } catch (StringIndexOutOfBoundsException e) {
                            System.out.println("[System]: Invalid command format");
                        }
                        continue;
                    }
                    broadcast("[Admin]: " + serverMessage, null);
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

                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.out.println("[System]: Server Error: " + RED + e.getMessage() + RESET);
        }
    }

    public static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender){
                broadcastPool.submit(() -> {
                    try {
                        client.sendMessage(message);
                    } catch (Exception e) {
                        System.out.println("[Error]: Error when trying to broadcast to \"" +
                                YELLOW + client.getClientName() + RESET + "\": " +
                                RED+ e.getMessage() + RESET);
                    }
                });
            }
        }
    }

    public static void broadcast(String command, Object data, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                broadcastPool.submit(() -> {
                    try {
                        client.sendResponse(command, data);
                    } catch (Exception e) {
                        System.out.println("[Error]: Broadcasting error to \"" + YELLOW + client.getClientName() + RESET + "\"");
                    }
                });
            }
        }
    }

    public static void privateMsg(String receiver, String message) {
        receiver = receiver.trim();
        for (ClientHandler client : clients) {
            if (client.getClientName() != null && client.getClientName().equals(receiver)) {
                client.sendMessage("[Admin]" + BLUE + " (private)" + RESET + ": " + message);
                return;
            }
        }
        System.out.println("[System]: User \"" + receiver + "\" doesn't exist");
    }

    public static void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
    }

    public static void kickTarget(String target, String reason) {
        ClientHandler targetToKick = null;
        for (ClientHandler client : clients) {
            if (client.getClientName() != null && client.getClientName().equalsIgnoreCase(target)) {
                targetToKick = client;
                break;
            }
        }
        if (targetToKick != null) {
            System.out.println("[System]: \"" + YELLOW + target + RESET + "\" has been kicked");
            targetToKick.forceDisconnect(reason);
        } else {
            System.out.println("[System]: User \"" + YELLOW + target + RESET + "\" doesn't exist");
        }
    }

    public static void getClientList() {
        int count = 0;
        if (clients.isEmpty()) {
            System.out.println("[System]: There are no clients connected");
        } else {
            System.out.println(GREEN + "=======================" + RESET);
            for (ClientHandler client : clients) {
                System.out.println(count + ". " + client.getClientName());
                count++;
            }
            System.out.println("Total: " + count + " clients");
            System.out.println(GREEN + "=======================" + RESET);
        }
    }

    public static void kickTargetByNumber(int i, String reason) {
        ClientHandler targetToKick = null;
        if (i >= 0 && i < clients.size()) {
            targetToKick = clients.get(i);
        }

        if (targetToKick != null) {
            System.out.println("[System]: \"" + YELLOW + targetToKick.getClientName() + RESET + "\" has been kicked");
            targetToKick.forceDisconnect(reason);
        } else {
            System.out.println("[System]: Client index " + i + " doesn't exist");
        }
    }
}