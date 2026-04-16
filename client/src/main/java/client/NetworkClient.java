package client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import network.NetworkMessage;
import javafx.application.Platform;

import java.awt.Desktop;
import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.util.function.Consumer;

public class NetworkClient {

    public static final String ANSI_RESET  = "\u001B[0m";
    public static final String ANSI_RED    = "\u001B[31m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE   = "\u001B[34m";
    public static final String ANSI_GREEN  = "\u001B[32m";

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Consumer<NetworkMessage> onMessageReceived;

    public NetworkClient(String serverAddress, int port) {
        System.out.println("=====================================");
        System.out.println("[System]: Trying to connect to server...");

        for (int i = 0; i < 5; i++) {
            try {
                socket = new Socket(serverAddress, port);
                socket.setSoTimeout(3000);

                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                NetworkMessage pingMsg = new NetworkMessage("PING", "Connecting request from client");
                out.println(mapper.writeValueAsString(pingMsg));

                String responseLine = in.readLine();

                if (responseLine == null) {
                    throw new IOException("Server is not on");
                }

                NetworkMessage response = mapper.readValue(responseLine, NetworkMessage.class);
                if (!"PONG".equals(response.getCommand())) {
                    throw new IOException("Invalid format from server");
                }

                socket.setSoTimeout(0);

                Thread listenerThread = new Thread(this::listenToServer);
                listenerThread.setDaemon(true);
                listenerThread.start();

                System.out.println(ANSI_GREEN + "[System]: Successfully connected" + ANSI_RESET);
                return;

            } catch (IOException e) {
                System.out.println(ANSI_YELLOW + "[System]: Failed at try " + (i + 1) + " - " + e.getMessage() + ANSI_RESET);
                try {
                    if (socket != null) socket.close();
                } catch (Exception ignored) {}

                if (i < 4) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        System.out.println(ANSI_BLUE + "[System]: Failed after 5 tries. Opening offline application" + ANSI_RESET);
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && out != null;
    }

    public void setOnMessageReceived(Consumer<NetworkMessage> callback) {
        this.onMessageReceived = callback;
    }

    public void sendMessage(String command, Object data) {
        if (!isConnected()) {
            System.out.println("[Error]: Cannot send command: '" + ANSI_YELLOW + command + ANSI_RESET + "' due to not connected");
            return;
        }

        try {
            NetworkMessage msg = new NetworkMessage(command, data);
            String json = mapper.writeValueAsString(msg);
            out.println(json);
        } catch (Exception e) {
            System.out.println("[Error]: JSON package error: " + ANSI_RED + e.getMessage() + ANSI_RESET);
        }
    }

    private void listenToServer() {
        try {
            String jsonMessage;
            while ((jsonMessage = in.readLine()) != null) {
                NetworkMessage response = mapper.readValue(jsonMessage, NetworkMessage.class);
                String command = response.getCommand();

                if ("REDIRECT".equals(command)) {
                    String url = (String) response.getData();
                    try {
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                            Desktop.getDesktop().browse(new URI(url));
                            System.out.println("[System]: Redirecting to: " + ANSI_YELLOW + url + ANSI_RESET);
                        }
                    } catch (Exception e) {
                        System.out.println("[Error]: Cannot redirect: " + ANSI_RED + e.getMessage() + ANSI_RESET);
                    }
                    continue;
                }

                if ("KICKED".equals(command)) {
                    System.out.println(ANSI_YELLOW + "[System]: You have been kicked. Reason: " + response.getData() + ANSI_RESET);
                    if (onMessageReceived != null) {
                        Platform.runLater(() -> onMessageReceived.accept(response));
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (Exception ignored) {}
                    System.exit(0);
                }

                if ("CHAT".equals(command)) {
                    System.out.println(response.getData());
                }

                if (onMessageReceived != null) {
                    Platform.runLater(() -> onMessageReceived.accept(response));
                }
            }
        } catch (IOException e) {
            System.out.println("[Error]: Lost connection to server: " + ANSI_RED + e.getMessage() + ANSI_RESET);
        }
    }
}