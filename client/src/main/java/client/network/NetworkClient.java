package client.network;

import client.handler.ResponseDispatcher;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import network.NetworkMessage;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.crypto.SecretKey;
import java.net.URI;
import java.security.PublicKey;
import java.util.function.Consumer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import utils.CryptoUtil;
import static utils.ConsoleColors.*;

/**
 * The core networking component for the client application.
 * <p>
 * This class establishes and maintains a WebSocket connection to the server.
 * It is responsible for:
 * <ul>
 *     <li>Attempting automatic connection retries (up to 5 times) with timeout handling.</li>
 *     <li>Performing the client-side of the RSA-AES hybrid cryptographic handshake.</li>
 *     <li>Encrypting outgoing payloads and decrypting incoming payloads seamlessly.</li>
 *     <li>Delegating received commands to the {@link ResponseDispatcher} or custom callbacks.</li>
 * </ul>
 */
public class NetworkClient {

    private AuctionWSClient wsClient;

    // Ignore unknown properties to prevent crashes on schema updates
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Consumer<NetworkMessage> onMessageReceived;
    private final ResponseDispatcher dispatcher = new ResponseDispatcher();

    // Security state
    private SecretKey myAesKey;
    private boolean isAesKeyEstablished = false;
    private CountDownLatch handshakeLatch;

    /**
     * Initializes the client and attempts to connect to the WebSocket server.
     * <p>
     * Implements a retry mechanism (max 5 attempts). It blocks the initializing thread
     * until both the WebSocket connection and the RSA-AES handshake are successfully completed,
     * or until it times out.
     *
     * @param serverAddress The server's IP address or domain.
     * @param port          The server's WebSocket port.
     */
    public NetworkClient(String serverAddress, int port) {
        System.out.println("===========================================");
        System.out.println("[System]: Connecting to server via WebSocket...");

        // Normalize URL to ws:// protocol
        String wsUrl = "ws://" + serverAddress + ":" + port;

        for (int i = 0; i < 5; i++) {
            try {
                wsClient = new AuctionWSClient(new URI(wsUrl));

                // Reset the synchronization latch for each connection attempt
                handshakeLatch = new CountDownLatch(1);

                // Block the thread until the WebSocket is OPEN or fails
                boolean connected = wsClient.connectBlocking();

                if (connected) {
                    // Block for up to 3 seconds waiting for the handshake to finish in the background
                    boolean isHandshakeDone = handshakeLatch.await(3, TimeUnit.SECONDS);

                    if (isHandshakeDone && isAesKeyEstablished) {
                        System.out.println("[System]:" + GREEN + " Successfully connected and secured!" + RESET);
                        return;
                    } else {
                        System.out.println("[System]:" + YELLOW + " Connected but AES Handshake timeout." + RESET);
                        wsClient.close();
                    }
                }
            } catch (Exception e) {
                System.out.println("[System]:" + YELLOW + " Failed at try " + (i + 1) + " - " + e.getMessage() + RESET);
            }

            if (i < 4) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        System.out.println("[System]:" + RED + " Failed after 5 tries to connect to " + wsUrl + RESET);
    }

    /**
     * Checks if the client is fully connected and the secure channel is established.
     *
     * @return true if connected and secured, false otherwise.
     */
    public boolean isConnected() {
        return wsClient != null && wsClient.isOpen() && isAesKeyEstablished;
    }

    public void setOnMessageReceived(Consumer<NetworkMessage> callback) {
        this.onMessageReceived = callback;
    }

    public Consumer<NetworkMessage> getOnMessageReceived() {
        return this.onMessageReceived;
    }

    /**
     * Serializes, encrypts, and sends a command and its associated data to the server.
     *
     * @param command The action command (e.g., "LOGIN", "CREATE_AUCTION").
     * @param data    The data payload to be serialized into JSON.
     */
    public void sendMessage(String command, Object data) {
        if (!isConnected()) {
            System.out.println("[System]: Cannot send command: \"" + YELLOW + command + RESET + "\" due to" + RED + " not being fully connected." + RESET);
            return;
        }

        try {
            NetworkMessage msg = new NetworkMessage(command, data);
            String json = mapper.writeValueAsString(msg);
            String encryptedPayload = CryptoUtil.encryptAES(json, myAesKey);

            // Use the non-blocking send method of Java-WebSocket
            wsClient.send(encryptedPayload);

        } catch (Exception e) {
            System.out.println("[System]: JSON package error: " + RED + e.getMessage() + RESET);
        }
    }

    // === INNER CLASS FOR WEBSOCKET EVENTS ===

    /**
     * The internal WebSocket client implementation.
     * Listens asynchronously for incoming frames and manages the connection lifecycle.
     */
    private class AuctionWSClient extends WebSocketClient {

        public AuctionWSClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            // Connection opened. Do nothing but wait for the server to send the RSA Public Key.
        }

        @Override
        public void onMessage(String message) {
            // Phase 1: If AES key is not set, the incoming message is STRICTLY the Server's RSA Public Key.
            if (!isAesKeyEstablished) {
                try {
                    PublicKey serverPublicKey = CryptoUtil.getPublicKeyFromBase64(message);

                    // Generate local AES key, encrypt it with Server's RSA, and send it back
                    myAesKey = CryptoUtil.generateAESKey();
                    String encryptedAesKey = CryptoUtil.encryptAESKeyWithRSA(myAesKey, serverPublicKey);
                    this.send(encryptedAesKey);

                    isAesKeyEstablished = true;
                } catch (Exception e) {
                    System.out.println("[System]: Handshake failed: " + RED + e.getMessage() + RESET);
                } finally {
                    // Always release the main thread block, regardless of success or failure
                    if (handshakeLatch != null) {
                        handshakeLatch.countDown();
                    }
                }
                return;
            }

            // Phase 2: Secure channel established. Decrypt and route standard JSON packages.
            try {
                String jsonMessage = CryptoUtil.decryptAES(message, myAesKey);
                NetworkMessage response = mapper.readValue(jsonMessage, NetworkMessage.class);

                dispatcher.dispatch(response, NetworkClient.this);

            } catch (Exception e) {
                System.out.println("[Warning]: Ignore invalid data package: " + YELLOW + e.getMessage() + RESET);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            isAesKeyEstablished = false; // Revoke security clearance
            System.out.println("[System]: Connection closed. Reason: " + YELLOW + reason + RESET);
        }

        @Override
        public void onError(Exception ex) {
            System.out.println("[System]: WebSocket Error: " + RED + ex.getMessage() + RESET);
        }
    }
}