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

/**
 * Handles individual client WebSocket connections.
 * <p>
 * This class is responsible for managing the lifecycle of a single client's session, including:
 * <ul>
 *     <li>Executing the RSA-AES hybrid cryptographic handshake for secure communication.</li>
 *     <li>Decrypting incoming payloads and routing them via the {@link CommandDispatcher}.</li>
 *     <li>Encrypting outgoing {@link NetworkMessage} responses and sending them non-blockingly.</li>
 *     <li>Managing connection state and graceful disconnections.</li>
 * </ul>
 * Because it operates on a WebSocket architecture (NIO), it is purely event-driven and
 * does not monopolize a dedicated thread.
 */
public class ClientHandler {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final WebSocket conn;
    private static int cNC = 0;
    private String clientName = "#Guest" + (cNC++);
    private User user;

    // ── 2FA session state ─────────────────────────────────────────────
    /** User đã qua bước verify password nhưng CHƯA qua 2FA.
     *  Chỉ tồn tại trong khoảng thời gian chờ VERIFY_2FA. */
    private User pendingUser;

    /** Secret TOTP tạm thời trong luồng SETUP, chưa được lưu DB.
     *  Xoá ngay sau khi CONFIRM_SETUP_2FA thành công hay thất bại. */
    private String pendingTotpSecret;

    public User getPendingUser()              { return pendingUser; }
    public void  setPendingUser(User user)    { this.pendingUser = user; }

    public String getPendingTotpSecret()           { return pendingTotpSecret; }
    public void   setPendingTotpSecret(String s)   { this.pendingTotpSecret = s; }

    private final UserController userController;
    private final CommandDispatcher dispatcher;

    // Ignore unknown JSON properties for robust parsing
    private final ObjectMapper mapper = JacksonConfig.mapper();

    private SecretKey sharedAesKey;
    private KeyPair rsaKeyPair;

    // Security state flag
    private boolean isAesKeyEstablished = false;

    /**
     * Constructs a new ClientHandler bound to a specific WebSocket connection.
     *
     * @param conn           The active WebSocket connection.
     * @param userController The controller handling user authentication and data.
     * @param dispatcher     The central command dispatcher for routing messages.
     */
    public ClientHandler(WebSocket conn, UserController userController, CommandDispatcher dispatcher) {
        this.conn = conn;
        this.userController = userController;
        this.dispatcher = dispatcher;
    }

    /**
     * Initiates the security handshake immediately after the WebSocket connection opens.
     * Generates an ephemeral RSA key pair and sends the public key to the client.
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
     * Processes raw text frames received from the WebSocket.
     * <p>
     * If the AES key is not yet established, it assumes the payload is the RSA-encrypted AES key.
     * Once secured, it decrypts AES payloads, parses them into JSON {@link NetworkMessage} objects,
     * and delegates execution to the {@link CommandDispatcher}.
     *
     * @param message The raw, encrypted text payload from the client.
     */
    public void processIncomingMessage(String message) {
        // Phase 1: Security Handshake
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

        // Phase 2: Standard Command Processing
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
     * Encrypts and transmits a response back to the client.
     *
     * @param command The action command (e.g., "LOGIN_SUCCESS", "UPDATE_AUCTION_PRICE").
     * @param data    The associated data payload to be serialized into JSON.
     */
    public void sendResponse(String command, Object data) {
        try {
            // Abort if connection is dead or insecure
            if (conn == null || !conn.isOpen() || !isAesKeyEstablished) return;

            NetworkMessage responseMsg = new NetworkMessage(command, data);
            String jsonOutput = mapper.writeValueAsString(responseMsg);
            String encryptedPayload = CryptoUtil.encryptAES(jsonOutput, sharedAesKey);

            // WebSocket conn.send() is inherently non-blocking and thread-safe
            conn.send(encryptedPayload);

        } catch (Exception e) {
            log.error("JSON serialization error: {}", e.getMessage());
        }
    }

    /**
     * Sends a plain text chat message to the client.
     *
     * @param message The chat content.
     */
    public void sendMessage(String message) {
        sendResponse("CHAT", message);
    }

    /**
     * Gracefully terminates the connection and removes the client from the active registry.
     */
    public void closeConnection() {
        ClientManager.removeClient(this);
        log.info("\"{}\" has disconnected.", clientName);
        if (conn != null && conn.isOpen()) {
            conn.close();
        }
    }

    /**
     * Forcibly disconnects the client, sending an explanatory message before closure.
     * Used primarily by administrators.
     *
     * @param reason The reason for the kick.
     */
    public void forceDisconnect(String reason) {
        sendResponse("KICKED", reason);
        if (conn != null && conn.isOpen()) {
            conn.closeConnection(1000, reason); // Code 1000: Normal Closure
        }
        closeConnection();
    }

    /**
     * Instructs the client's GUI to open a specific web URL.
     *
     * @param url The target website.
     */
    public void redirectToWebsite(String url) {
        sendResponse("REDIRECT", url);
    }

    // === GETTERS & SETTERS ===

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public UserController getUserController() {
        return userController;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public static int getcNC() {
        return cNC;
    }

    public static void incrementcNC() {
        cNC++;
    }
}