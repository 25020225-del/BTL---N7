package server;

import controller.AuctionMonitor;
import controller.UserController;
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
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_GREEN = "\u001B[32m";

    private static final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor();

    private static final String BIN_ID="69d4960b856a6821890813a2";
    private static final Dotenv dotenv=Dotenv.load();
    private static final String JSONBIN_KEY=dotenv.get("JSONBIN_API_KEY");
    private static final String LOCALTONET_TOKEN=dotenv.get("LOCALTONET_API_TOKEN");

    private static final List<ClientHandler>clients=new java.util.concurrent.CopyOnWriteArrayList<>();

    private static String lastSyncedIp = "";
    private static int lastSyncedPort = -1;

    // Controller set
    private static final UserController userController=new UserController();

    // System auction list
    public static final List<Auction> danhSachDauGia=new ArrayList<>();

    public static void updateBulletinBoard(String currentIp,int currentPort){
        try{
            String urlString="https://api.jsonbin.io/v3/b/"+BIN_ID.trim();
            URL url=new URL(urlString);

            HttpURLConnection conn=(HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type","application/json");
            conn.setRequestProperty("X-Master-Key",JSONBIN_KEY);
            conn.setDoOutput(true);

            String jsonInputString="{\"ip\": \""+currentIp+"\", \"port\": "+currentPort+"}";

            try (OutputStream os=conn.getOutputStream()) {
                byte[] input=jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input,0,input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                System.out.println("[JSONBin]: New IP - Port synced: "+ANSI_YELLOW+currentIp+":"+currentPort+ANSI_RESET);
            } else {
                System.err.println("[JSONBin]: Error:" +ANSI_RED+ responseCode +ANSI_RESET+ " at URL: " +ANSI_YELLOW+ urlString+ANSI_RESET);

                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                    System.err.println("Error details: " + br.readLine());
                }
            }
        } catch (Exception e) {
            System.out.println("[JSONBin]: Connection Error: "+ANSI_RED+e.getMessage()+ANSI_RESET);
        }
    }

    private static String[] getLocaltonetAddress() {
        try {
            URL url=new URL("https://localtonet.com/api/GetTunnels");
            HttpURLConnection conn=(HttpURLConnection)url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization","Bearer "+LOCALTONET_TOKEN);
            conn.setRequestProperty("Accept", "application/json");

            BufferedReader in=new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder content=new StringBuilder();
            while((inputLine=in.readLine())!= null) content.append(inputLine);
            in.close();

            String jsonResponse=content.toString();

            String ip="";
            String port="";

            Matcher ipMatcher=Pattern.compile("\"serverDomain\":\"([^\"]+)\"").matcher(jsonResponse);
            if(ipMatcher.find()) ip=ipMatcher.group(1);

            Matcher portMatcher=Pattern.compile("\"serverPort\":(\\d+)").matcher(jsonResponse);
            if(portMatcher.find()) port=portMatcher.group(1);

            if(!ip.isEmpty()&&!port.isEmpty()) return new String[]{ip,port};
        }catch(Exception e){
            System.out.println("[System]: Localtonet API Error: "+ANSI_RED+e.getMessage()+ANSI_RESET);
        }
        return null;
    }

    public static void main(String[] args){
        final int PORT=6969;
        scheduler.scheduleAtFixedRate(()->{
            try {
                String[]publicAddress=getLocaltonetAddress();

                if (publicAddress!=null) {
                    String newIp = publicAddress[0];
                    int newPort = Integer.parseInt(publicAddress[1]);

                    if (!newIp.equals(lastSyncedIp) || newPort != lastSyncedPort) {
                        System.out.println(ANSI_YELLOW+"\n[Auto-Sync]: Localtonet address change detected. Updating JSONBin"+ANSI_RESET);
                        updateBulletinBoard(newIp, newPort);

                        lastSyncedIp = newIp;
                        lastSyncedPort = newPort;
                        System.out.println(ANSI_GREEN+"[Auto-Sync]: Successfully synced: "+ANSI_YELLOW+newIp+":"+newPort+ANSI_RESET);
                    }
                } else {
                    System.out.println("[Auto-Sync]: Error: "+ANSI_RED+"Cannot call API"+ANSI_RESET);
                }
            } catch (Exception e) {
                System.err.println("[Auto-Sync]: System error: "+ANSI_RED+e.getMessage()+ANSI_RESET);
            }
        }, 0, 30, TimeUnit.SECONDS);

        System.out.println("[System]: Getting address");
        String[] publicAddress = getLocaltonetAddress();

        if (publicAddress!=null){
            updateBulletinBoard(publicAddress[0], Integer.parseInt(publicAddress[1]));
        }else{
            System.out.println(ANSI_BLUE+"[System]: Cannot get Localtonet address. Use localhost"+ANSI_RESET);
            updateBulletinBoard("127.0.0.1", PORT);
        }

        database.DatabaseManager.initializeDatabase();
        // 2. Initialize and activate time monitor
        AuctionMonitor monitor = new AuctionMonitor(danhSachDauGia);
        monitor.startMonitoring();

        // ShutdownHook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            broadcast(ANSI_YELLOW+"[System]: Server is being closed. Every connecting client will be disconnected in a moment"+ANSI_RESET, null);
            broadcast(ANSI_YELLOW+"[System]: Server has been shutdown"+ANSI_RESET, null);

            // 3. Safely turn off monitor
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
                            System.out.println("[System] Error: "+ANSI_RED+"Index of /kickn command must be an integer"+ANSI_RESET);
                        }
                        continue;
                    }
                    if (serverMessage.startsWith("/redirect ")) {
                        String[]data=serverMessage.substring(10).split(" ");
                        for(ClientHandler client:clients){
                            if(client.getClientName().equals(data[0])){
                                client.redirectToWebsite(data[1]);
                            }
                        }
                        continue;
                    }
                    if (serverMessage.startsWith("/msg ")) {
                        String[]data=serverMessage.substring(5).split(" ");
                        privateMsg(data[0],data[1]);
                        continue;
                    }
                    broadcast("[Admin]: "+serverMessage, null);
                }
            }
        });
        serverChatThread.start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[System]: Server is running on port "+ANSI_YELLOW+PORT+ANSI_RESET);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[System]: New client connected from: "+ANSI_YELLOW+socket.getInetAddress().getHostAddress()+ANSI_RESET);

                ClientHandler clientHandler = new ClientHandler(socket, userController);

                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.out.println("[System]: Server Error: "+ANSI_RED+e.getMessage()+ANSI_RESET);
        }
    }

    // Broadcasts a message to all connected clients except the sender
    public static void broadcast(String message, ClientHandler sender) {
        for(ClientHandler client:clients) if(client!=sender) client.sendMessage(message);
    }

    public static void privateMsg(String receiver, String message){
        receiver=receiver.trim();
        for(ClientHandler client:clients) {
            if (client.getClientName().equals(receiver)) {
                client.sendMessage("[Admin]"+ANSI_BLUE+"(private)"+ANSI_RESET+": "+message);
                break;
            }
            System.out.println("[System]: \""+receiver+"\" doesn't exist");
        }
    }

    public static void removeClient(ClientHandler clientHandler){clients.remove(clientHandler);}

    public static void kickTarget(String target, String reason){
        ClientHandler targetToKick=null;
        for(ClientHandler client:clients){
            if(client.getClientName()!=null&&client.getClientName().equalsIgnoreCase(target)){
                targetToKick=client;
                break;
            }
        }
        if(targetToKick!=null){
            System.out.println("[System]: \""+ANSI_YELLOW+target+ANSI_RESET+"\" has been kicked");
            targetToKick.forceDisconnect(reason);
        }else{
            System.out.println("[System]: ID \""+ANSI_YELLOW+target+ANSI_RESET+"\" doesn't exist");
        }
    }

    public static void getClientList(){
        int count=0;
        if(clients.isEmpty()) System.out.println("[System]: There's no client");
        else{
            System.out.println(ANSI_GREEN+"======================="+ANSI_RESET);
            for (ClientHandler client:clients) {
                System.out.println(count+". "+client.getClientName());
                count++;
            }
            System.out.println("Total: "+count+" clients");
            System.out.println(ANSI_GREEN+"======================="+ANSI_RESET);
        }
    }

    public static void kickTargetByNumber(int i, String reason){
        ClientHandler targetToKick=null;
        if(i<clients.size()) targetToKick=clients.get(i);
        if(targetToKick!=null){
            System.out.println("[System]: \""+ANSI_YELLOW+targetToKick.getClientName()+ANSI_RESET+"\" has been kicked");
            targetToKick.forceDisconnect(reason);
        }else System.out.println("[System]: "+i+". client doesn't exist");
    }
}