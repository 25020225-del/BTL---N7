package server;

import com.fasterxml.jackson.databind.ObjectMapper;
import controller.UserController;
import model.user.User;
import network.NetworkMessage;
import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ServerExtension.ClientManager;
import server.handler.CommandDispatcher;
import utils.CryptoUtil;
import utils.JacksonConfig;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stateful connection handler governing the lifecycle of individual client WebSocket sessions.
 * Manages hybrid RSA-AES cryptographic handshakes, packet decryption, and dispatching pipelines.
 */
public class ClientHandler {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);
    private static final AtomicInteger clientCounter = new AtomicInteger(0);

    private final WebSocket conn;
    private final UserController userController;
    private final CommandDispatcher dispatcher;
    private final ObjectMapper mapper = JacksonConfig.mapper();

    private String clientName = "#Guest" + clientCounter.getAndIncrement();
    private User user;
    private User pendingUser;
    private String pendingTotpSecret;
    private SecretKey sharedAesKey;
    private KeyPair rsaKeyPair;
    private boolean isAesKeyEstablished = false;

    public ClientHandler(WebSocket conn, UserController userController, CommandDispatcher dispatcher) {
        this.conn = conn;
        this.userController = userController;
        this.dispatcher = dispatcher;
    }

    /**
     * Executes the initial step of the cryptographic handshake by generating
     * an ephemeral RSA KeyPair and exporting the Public Key encoded in Base64.
     */
    public void startHandshake() {
        try {
            rsaKeyPair = CryptoUtil.generateRSAKeyPair();
            String publicKeyBase64 = Base64.getEncoder().encodeToString(rsaKeyPair.getPublic().getEncoded());
            conn.send(publicKeyBase64);
        } catch (Exception e) {
            log.error("RSA Key generation failed in ClientHandler: {}", e.getMessage());
        }
    }

    /**
     * Ingress packet pipeline entry-point. Resolves security key exchanges if unestablished,
     * or decrypts incoming AES frames and dispatches unmarshalled messages.
     *
     * @param message encrypted raw incoming text frame from remote socket
     */
    public void processIncomingMessage(String message) {
        if (!isAesKeyEstablished) {
            try {
                sharedAesKey = CryptoUtil.decryptAESKeyWithRSA(message, rsaKeyPair.getPrivate());
                isAesKeyEstablished = true;
                log.info("Handshake success with \"{}\". AES Channel established.", clientName);
            } catch (Exception e) {
                log.warn("Handshake failed with {}", clientName);
                closeConnection();
            }
            return;
        }

        try {
            String jsonMessage = CryptoUtil.decryptAES(message, sharedAesKey);
            log.debug("Decrypted JSON from Client: {}", jsonMessage);

            NetworkMessage netMsg = mapper.readValue(jsonMessage, NetworkMessage.class);
            if (netMsg.getCommand() == null) {
                log.warn("\"{}\" sent a null command.", clientName);
                sendResponse("ERROR", "Command cannot be null");
                return;
            }

            dispatcher.dispatch(netMsg, this);

        } catch (Exception e) {
            log.error("Invalid JSON format or Decryption error: {}", e.getMessage());
            sendResponse("ERROR", "Invalid JSON format");
        }
    }

    /**
     * Serializes and cryptographically seals a transport message before piping down the socket.
     *
     * @param command outbound execution header route key descriptor
     * @param data    un-serialized outbound payload metadata object
     */
    public void sendResponse(String command, Object data) {
        try {
            if (conn == null || !conn.isOpen() || !isAesKeyEstablished) return;

            NetworkMessage responseMsg = new NetworkMessage(command, data);
            String jsonOutput = mapper.writeValueAsString(responseMsg);
            String encryptedPayload = CryptoUtil.encryptAES(jsonOutput, sharedAesKey);
            conn.send(encryptedPayload);

        } catch (Exception e) {
            log.error("JSON serialization error: {}", e.getMessage());
        }
    }

    public static int nextClientNumber() {
        return clientCounter.getAndIncrement();
    }

    public void sendMessage(String message) {
        sendResponse("CHAT", message);
    }

    /**
     * Gracefully evicts connection resources from system mapping and unbinds socket parameters.
     */
    public void closeConnection() {
        ClientManager.removeClient(this);
        log.info("\"{}\" has disconnected.", clientName);
        if (conn != null && conn.isOpen()) {
            conn.close();
        }
    }

    /**
     * Forcibly drops connection vectors after transmitting specific violation descriptions.
     *
     * @param reason explanatory eviction description message
     */
    public void forceDisconnect(String reason) {
        sendResponse("KICKED", reason);
        if (conn != null && conn.isOpen()) {
            conn.closeConnection(1000, reason);
        }
        closeConnection();
    }

    public void redirectToWebsite(String url) {
        sendResponse("REDIRECT", url);
    }

    /**
     * Encrypts and transmits a pre-serialized JSON payload directly down the WebSocket.
     * Prevents double-wrapping payloads that have already been formatted as NetworkMessages.
     *
     * @param jsonPayload pre-serialized JSON string of a NetworkMessage
     */
    public void sendPreSerializedResponse(String jsonPayload) {
        try {
            if (conn == null || !conn.isOpen() || !isAesKeyEstablished) return;
            String encryptedPayload = CryptoUtil.encryptAES(jsonPayload, sharedAesKey);
            conn.send(encryptedPayload);
        } catch (Exception e) {
            log.error("Encryption or transmission of pre-serialized response failed: {}", e.getMessage());
        }
    }

    public User getPendingUser() { return pendingUser; }
    public void setPendingUser(User user) { this.pendingUser = user; }
    public String getPendingTotpSecret() { return pendingTotpSecret; }
    public void setPendingTotpSecret(String s) { this.pendingTotpSecret = s; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public UserController getUserController() { return userController; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}