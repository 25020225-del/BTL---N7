package server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import controller.UserController;
import network.NetworkMessage;
import server.ServerExtension.ClientManager;
import server.ClientHandlerExtension.*;

import java.io.*;
import java.net.Socket;
import java.security.KeyPair;
import java.util.Base64;
import javax.crypto.SecretKey;

import utils.CryptoUtil;

import static utils.ConsoleColors.*;

public class ClientHandler implements Runnable {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private int cNC = 0;
    private volatile String clientName = "Guest" + (cNC++);
    private model.User user;

    private UserController userController;
    private static final CommandDispatcher dispatcher = new CommandDispatcher();

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private SecretKey sharedAesKey;
    private KeyPair rsaKeyPair;

    public ClientHandler(Socket socket, UserController userController) {
        this.socket = socket;
        this.userController = userController;

        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            System.out.println("[Error]: I/O exception in ClientHandler: " + RED + e.getMessage() + RESET);
        }
    }

    @Override
    public void run() {
        try {
            // ---HANDSHAKE---
            // 1. Generate an RSA key pair and send the public key to the client
            rsaKeyPair = CryptoUtil.generateRSAKeyPair();
            String publicKeyBase64 = Base64.getEncoder().encodeToString(rsaKeyPair.getPublic().getEncoded());
            out.println(publicKeyBase64);

            // 2. Wait for the client to send back the encrypted AES key
            String encryptedAesKey = in.readLine();
            if (encryptedAesKey == null) return;
            sharedAesKey = CryptoUtil.decryptAESKeyWithRSA(encryptedAesKey, rsaKeyPair.getPrivate());
            System.out.println("[Security]: Handshake success with \"" + YELLOW + clientName + RESET + "\". AES Channel established.");
            // ---END HANDSHAKE---

            String encryptedJsonMessage;
            while ((encryptedJsonMessage = in.readLine()) != null) {
                System.out.println("[System]: Getting encrypted JSON from Client: " + YELLOW + encryptedJsonMessage + RESET);

                try {
                    // Decrypting by AES
                    String jsonMessage = CryptoUtil.decryptAES(encryptedJsonMessage, sharedAesKey);
                    System.out.println("[System]: Decrypted JSON from Client: " + YELLOW + jsonMessage + RESET);

                    NetworkMessage message = mapper.readValue(jsonMessage, NetworkMessage.class);

                    if (message.getCommand() == null) {
                        System.out.println("[System]: \"" + YELLOW + clientName + RESET + "\" tried to send a null command");
                        sendResponse("ERROR", "Command cannot be null");
                        continue;
                    }

                    dispatcher.dispatch(message, this);

                } catch (Exception e) {
                    System.out.println("[Error]: Invalid JSON format: " + RED + e.getMessage() + RESET);
                    sendResponse("ERROR", "Invalid JSON format");
                }
            }
        } catch (IOException e) {
            System.out.println("[System]: Lost connection with " + (clientName != null ? clientName : "unknown Client"));
        } finally {
            closeConnection();
        }
    }

    public void sendResponse(String command, Object data) {
        try {
            NetworkMessage responseMsg = new NetworkMessage(command, data);
            String jsonOutput = mapper.writeValueAsString(responseMsg);
            String encryptedPayload = CryptoUtil.encryptAES(jsonOutput, sharedAesKey);
            out.println(encryptedPayload);
        } catch (Exception e) {
            System.out.println("[Error]: JSON serialization: " + RED + e.getMessage() + RESET);
        }
    }

    public void sendMessage(String message) {
        sendResponse("CHAT", message);
    }

    private void closeConnection() {
        ClientManager.removeClient(this);
        System.out.println("[System]: \"" + YELLOW + clientName + RESET + "\" has stopped connecting");
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.out.println("[Error]: Socket closing error: " + RED + e.getMessage() + RESET);
        }
    }

    public void forceDisconnect(String reason) {
        sendResponse("KICKED", reason);
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.out.println("[Error]: Socket closing error: " + RED + e.getMessage() + RESET);
        }
    }

    public void redirectToWebsite(String url) {
        sendResponse("REDIRECT", url);
    }

    public String getClientName() {
        return clientName;
    }
    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public UserController getUserController() {
        return userController;
    }

    public model.User getUser() {return user;}
    public void setUser(model.User user) {this.user = user;}
}