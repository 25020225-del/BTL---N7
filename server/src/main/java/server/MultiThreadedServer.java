package server;

import controller.AuctionMonitor;
import model.Auction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MultiThreadedServer {
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static final String BIN_ID="69d4960b856a6821890813a2";
    private static final Dotenv dotenv = Dotenv.load();
    private static final String JSONBIN_KEY = Dotenv.load().get("JSONBIN_API_KEY");
    private static final String LOCALTONET_TOKEN = dotenv.get("LOCALTONET_API_TOKEN");
    private static final List<ClientHandler> clients = new ArrayList<>();

    // 1. THÊM DANH SÁCH ĐẤU GIÁ CHUNG CỦA TOÀN HỆ THỐNG
    public static final List<Auction> danhSachDauGia = new ArrayList<>();

    public static void updateBulletinBoard(String currentIp, int currentPort) {
        try {
            String urlString = "https://api.jsonbin.io/v3/b/" + BIN_ID.trim();
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
                System.out.println("[JSONBin] New IP - Port synced: " + currentIp + ":" + currentPort);
            } else {
                System.err.println("[JSONBin] Error:" + responseCode + " at URL: " + urlString);

                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                    System.err.println("Error details: " + br.readLine());
                }
            }
        } catch (Exception e) {
            System.err.println("[JSONBin] Connection Error: " + e.getMessage());
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
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();

            String jsonResponse = content.toString();

            String ip = "";
            String port = "";

            Matcher ipMatcher = Pattern.compile("\"serverDomain\":\"([^\"]+)\"").matcher(jsonResponse);
            if (ipMatcher.find()) {
                ip = ipMatcher.group(1);
            }

            Matcher portMatcher = Pattern.compile("\"serverPort\":(\\d+)").matcher(jsonResponse);
            if (portMatcher.find()) {
                port = portMatcher.group(1);
            }

            if (!ip.isEmpty() && !port.isEmpty()) {
                return new String[]{ip, port};
            }
        } catch (Exception e) {
            System.out.println("[System] Localtonet API Error: " + e.getMessage());
        }
        return null;
    }

    public static void main(String[] args) {
        final int PORT = 6969;

        scheduler.scheduleAtFixedRate(()->{
            try {
                System.out.println("\n[Auto-Sync] Checking new address from Localtonet...");
                String[] publicAddress = getLocaltonetAddress();
                if(publicAddress!=null){
                    String newIp=publicAddress[0];
                    int newPort=Integer.parseInt(publicAddress[1]);
                    updateBulletinBoard(newIp,newPort);
                    System.out.println("[Auto-Sync] Synced onto JSONBin: "+newIp+ ":"+newPort);
                }else{
                    System.err.println("[Auto-Sync] Error: Cannot get info from Localtonet API.");
                }
                }catch(Exception e){
                    System.err.println("[Auto-Sync] System error: "+e.getMessage());
                }
        },0,5,TimeUnit.MINUTES);

        System.out.println("[System] Getting address");
        String[] publicAddress = getLocaltonetAddress();

        if (publicAddress!=null){
            updateBulletinBoard(publicAddress[0], Integer.parseInt(publicAddress[1]));
        }else{
            System.out.println("[System] Cannot get Localtonet address. Use localhost");
            updateBulletinBoard("127.0.0.1", PORT);
        }

        database.DatabaseManager.initializeDatabase();
        // 2. KHỞI TẠO VÀ BẬT HỆ THỐNG GIÁM SÁT THỜI GIAN
        AuctionMonitor monitor = new AuctionMonitor(danhSachDauGia);
        monitor.startMonitoring();

        // ShutdownHook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            broadcast("[System] Server is being closed. Every connecting client will be disconnected in a moment", null);
            broadcast("[System] Server has been shutdown", null);

            // 3. Tắt monitor an toàn khi tắt Server
            monitor.stopMonitoring();
        }));

        // Thread allowing the Server Admin to type and send messages to all clients
        Thread serverChatThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                if (scanner.hasNextLine()) {
                    String serverMessage = scanner.nextLine();
                    if (serverMessage.startsWith("/kick ")) {
                        String target = serverMessage.substring(6);
                        System.out.println("Reason: ");
                        String reason = scanner.nextLine();
                        kickTarget(target, reason);
                        continue;
                    }
                    if (serverMessage.startsWith("/clist")){
                        getClientList();
                        continue;
                    }
                    if (serverMessage.startsWith("/kickn ")) {
                        try{
                            String index = serverMessage.substring(7);
                            System.out.println("Reason: ");
                            String reason = scanner.nextLine();
                            kickTargetByNumber(Integer.parseInt(index), reason);
                        }catch(NumberFormatException e){
                            System.out.println("[System] Error: Index of /kickn command must be an integer");
                        }
                        continue;
                    }
                    if (serverMessage.startsWith("/redirect ")) {
                        String url = serverMessage.substring(10);
                        broadcast("[Admin] REDIRECT:" + url, null);
                        continue;
                    }
                    broadcast("[Admin]: "+serverMessage, null);
                }
            }
        });
        serverChatThread.start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[System] Server is running on port " + PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[System] New client connected from: " + socket.getInetAddress().getHostAddress());
                ClientHandler clientHandler = new ClientHandler(socket);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("[System] Server Error: " + e.getMessage());
        }
    }

    // Broadcasts a message to all connected clients except the sender
    public static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) client.sendMessage(message);
        }
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
            System.out.println("[System] \"" + target + "\" has been kicked");
            targetToKick.forceDisconnect(reason);
        } else {
            System.out.println("[System] ID \"" + target + "\" doesn't exist");
        }
    }
    public static void getClientList(){
        int count = 0;
        for (ClientHandler client : clients) {
            System.out.println(count+". "+client.getClientName());
            count++;
        }
    }
    public static void kickTargetByNumber(int i, String reason) {
        ClientHandler targetToKick = null;
        if(i<clients.size()) targetToKick=clients.get(i);
        if (targetToKick != null) {
            System.out.println("[System] \""+targetToKick.getClientName() + "\" has been kicked");
            targetToKick.forceDisconnect(reason);
        } else {
            System.out.println("[System] "+i+". client doesn't exist");
        }
    }
}