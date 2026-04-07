package client;

import io.github.cdimascio.dotenv.Dotenv;
import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class Client {
    private static final String BIN_ID = "69d4960b856a6821890813a2";

    public static void main(String[] args) {
        System.out.println("Getting server address from API...");
        String[] serverInfo = getServerAddress();
        if (serverInfo == null || serverInfo.length == 0) {
            System.err.println("Server address not found");
            return;
        }
        final String SERVER_IP = serverInfo[0];
        final int SERVER_PORT = Integer.parseInt(serverInfo[1]);
        try {
            Socket socket = new Socket(SERVER_IP, SERVER_PORT);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Scanner scanner = new Scanner(System.in);
            // Thread for receiving messages from the server
            Thread receiveThread = new Thread(() -> {
                try {
                    String serverMessage;
                    serverMessage = in.readLine();
                    System.out.println(serverMessage);
                    while ((serverMessage = in.readLine()) != null) {
                        System.out.println("\n" + serverMessage);
                    }
                } catch (IOException e) {
                    System.out.println("\nDisconnected from the Server.");
                }
            });
            receiveThread.start();
            // Main thread for sending messages
            System.out.println("Connected to the Server. Type \"STOP\" to stop.");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            while (true) {
                String messageToSend = scanner.nextLine();
                out.println(messageToSend);
                if ("STOP".equalsIgnoreCase(messageToSend.trim())) {
                    System.out.println("Disconnecting in progress...");
                    socket.close();
                    break;
                }
                if ("OPEN AUCTION".trim().equalsIgnoreCase(messageToSend.trim())) {
                    System.out.println("Opening auction...");
                    ClientCallLauncher launcher = new ClientCallLauncher();
                    new Thread(launcher).start();
                    System.out.println("Auction opened.");
                }
            }
        } catch (IOException e) {
            System.err.println("Cannot connect to the Server: " + e.getMessage());
        }
    }

    private static String[] getServerAddress() {
        try {
            URL url = new URL("https://api.jsonbin.io/v3/b/" + BIN_ID);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Bin-Meta", "false");

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
            if (ipMatcher.find()) {
                ip = ipMatcher.group(1);
            }

            Matcher portMatcher = Pattern.compile("\"port\"\\s*:\\s*(\\d+)").matcher(jsonResponse);
            if (portMatcher.find()) {
                port = portMatcher.group(1);
            }

            if (!ip.isEmpty() && !port.isEmpty()) {
                return new String[]{ip, port};
            } else {
                System.out.println("Bulletin Board Data (Debug): " + jsonResponse);
            }

        } catch (Exception e) {
            System.out.println("Bulletin Board Error: " + e.getMessage());
        }
        return null;
    }
}
