package client.network;

import client.handler.ResponseDispatcher;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import network.NetworkMessage;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;
import java.util.Base64;
import javax.crypto.SecretKey;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import utils.CryptoUtil;
import static utils.ConsoleColors.*;

public class NetworkClient {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Consumer<NetworkMessage> onMessageReceived;
    private final ResponseDispatcher dispatcher = new ResponseDispatcher();

    private SecretKey myAesKey;

    public NetworkClient(String serverAddress, int port) {
        System.out.println("===========================================");
        System.out.println("[System]: Trying to connect to server...");

        for (int i = 0; i < 5; i++) {
            try {
                socket = new Socket(serverAddress, port);
                socket.setSoTimeout(3000);

                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // --- HANDSHAKE ---
                // 1. Retrieve RSA public key from server
                String publicKeyBase64 = in.readLine();
                if (publicKeyBase64 == null) throw new IOException("Server is not responding to handshake");

                PublicKey serverPublicKey = CryptoUtil.getPublicKeyFromBase64(publicKeyBase64);

                // 2. Generate the AES key, encrypt it using RSA, and send it back to the server
                myAesKey = CryptoUtil.generateAESKey();
                String encryptedAesKey = CryptoUtil.encryptAESKeyWithRSA(myAesKey, serverPublicKey);
                out.println(encryptedAesKey);
                // --- END HANDSHAKE ---

                // AES connection test (replacing old PING command)
                NetworkMessage pingMsg = new NetworkMessage("PING", "Connecting request from client");
                String pingJson = mapper.writeValueAsString(pingMsg);
                out.println(CryptoUtil.encryptAES(pingJson, myAesKey)); // PING Encoding

                String responseLine = in.readLine();
                if (responseLine == null) throw new IOException("Server is not on");

                // Decoding PONG
                String decryptedPong = CryptoUtil.decryptAES(responseLine, myAesKey);
                NetworkMessage response = mapper.readValue(decryptedPong, NetworkMessage.class);
                if (!"PONG".equals(response.getCommand())) {
                    throw new IOException("Invalid format from server");
                }

                socket.setSoTimeout(0);

                Thread listenerThread = new Thread(this::listenToServer);
                listenerThread.setDaemon(true);
                listenerThread.start();

                System.out.println("[System]:" + GREEN + "Successfully connected" + RESET);
                return;

            } catch (IOException e) {
                System.out.println("[System]:" + YELLOW + " Failed at try " + (i + 1) + " - " + e.getMessage() + RESET);
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
        System.out.println("[System]:" + RED + " Failed after 5 tries to connect to " + serverAddress + ":" + port + RESET);
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
            System.out.println("[Error]: Cannot send command: \"" + YELLOW + command + RESET + "\" due to" + RED + " not being connected" + RESET);
            return;
        }

        try {
            NetworkMessage msg = new NetworkMessage(command, data);
            String json = mapper.writeValueAsString(msg);
            String encryptedPayload = CryptoUtil.encryptAES(json, myAesKey);
            out.println(encryptedPayload);
        } catch (Exception e) {
            System.out.println("[Error]:" + RED + " JSON package error: " + e.getMessage() + RESET);
        }
    }

    private void listenToServer() {
        try {
            String encryptedMessage;
            while ((encryptedMessage = in.readLine()) != null) {
                String jsonMessage = CryptoUtil.decryptAES(encryptedMessage, myAesKey);
                NetworkMessage response = mapper.readValue(jsonMessage, NetworkMessage.class);

                dispatcher.dispatch(response, this);
            }
        } catch (Exception e) {
            System.out.println("[Error]: Lost connection or Decryption failed: " + RED + e.getMessage() + RESET);
        }
    }
}