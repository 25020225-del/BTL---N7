package client.network;

import client.handler.ResponseDispatcher;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import network.NetworkMessage;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

import static utils.ConsoleColors.*;

public class NetworkClient {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Consumer<NetworkMessage> onMessageReceived;
    private final ResponseDispatcher dispatcher = new ResponseDispatcher();

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

                System.out.println(GREEN + "[System]: Successfully connected" + RESET);
                return;

            } catch (IOException e) {
                System.out.println(YELLOW + "[System]: Failed at try " + (i + 1) + " - " + e.getMessage() + RESET);
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
        System.out.println(BLUE + "[System]: Failed after 5 tries. Opening offline application" + RESET);
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && out != null;
    }

    public void setOnMessageReceived(Consumer<NetworkMessage> callback) {
        this.onMessageReceived = callback;
    }

    public Consumer<NetworkMessage> getOnMessageReceived() {
        return this.onMessageReceived;
    }

    public void sendMessage(String command, Object data) {
        if (!isConnected()) {
            System.out.println("[Error]: Cannot send command: '" + YELLOW + command + RESET + "' due to not connected");
            return;
        }

        try {
            NetworkMessage msg = new NetworkMessage(command, data);
            String json = mapper.writeValueAsString(msg);
            out.println(json);
        } catch (Exception e) {
            System.out.println("[Error]: JSON package error: " + RED + e.getMessage() + RESET);
        }
    }

    private void listenToServer() {
        try {
            String jsonMessage;
            while ((jsonMessage = in.readLine()) != null) {
                NetworkMessage response = mapper.readValue(jsonMessage, NetworkMessage.class);

                dispatcher.dispatch(response, this);
            }
        } catch (IOException e) {
            System.out.println("[Error]: Lost connection to server: " + RED + e.getMessage() + RESET);
        }
    }
}