package client;

import io.github.cdimascio.dotenv.Dotenv;
import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.Desktop;
import java.net.URI;
public class Client {
    private static final String BIN_ID = "69d4960b856a6821890813a2";
    private static volatile boolean isConnected=false;
    static boolean LS=false;
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
            isConnected=true;

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
                        if (serverMessage.startsWith("[Admin] REDIRECT:")) {
                            String url = serverMessage.substring(17);
                            try {
                                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                                    Desktop.getDesktop().browse(new URI(url));
                                    System.out.println("[System] Redirecting you to " + url);
                                }
                            } catch (Exception e) {
                                System.out.println("[System] Can't open browser: " + e.getMessage());
                            }
                            continue;
                        }
                        if (serverMessage.contains("[System] Your ID has been successfully recognised.")) {
                            // Activate Launcher
                            if(!LS) {
                                LS=true;
                                ClientCallLauncher launcher = new ClientCallLauncher();
                                new Thread(launcher).start();
                                System.out.println("[System] Auction system has been launched");
                                out.println("Opened Auction system");
                            }
                        }
                        if (serverMessage.contains("You have been kicked by Admin")) {
                            isConnected = false;
                            System.out.println("Application exited");
                            System.exit(0);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("\nDisconnected from the Server.");
                    System.out.println("Application exited");
                    System.exit(0);
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

                if(!isConnected) {
                    System.out.println("[System] You are not connected to the server");
                    System.exit(0);
                }

                out.println(messageToSend);

                if ("STOP".equalsIgnoreCase(messageToSend.trim())) {
                    System.out.println("Disconnecting in progress...");
                    socket.close();
                    break;
                }

            }
        } catch (IOException e) {
            System.out.println("Cannot connect to the Server: " + e.getMessage());
        } finally {
            System.out.println("Application exited");
            System.exit(0);
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
    public static void disableLauncherNotifier(){
        System.out.println("Auction system has been closed");
    }
}
