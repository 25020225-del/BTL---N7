package client.network;

import client.handler.ResponseDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import network.NetworkMessage;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.CryptoUtil;
import utils.JacksonConfig;

import javax.crypto.SecretKey;
import java.net.URI;
import java.security.PublicKey;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
    private static final Logger log = LoggerFactory.getLogger(NetworkClient.class);

    private AuctionWSClient wsClient;

    // Ignore unknown properties to prevent crashes on schema updates
    private final ObjectMapper mapper = JacksonConfig.mapper();

    private Consumer<NetworkMessage> onMessageReceived;
    private final ResponseDispatcher dispatcher = new ResponseDispatcher();

    // Security state
    private SecretKey myAesKey;
    private boolean isAesKeyEstablished = false;
    private CountDownLatch handshakeLatch;

    /**
     * Initializes the client and attempts to connect to the WebSocket server using the provided full URL.
     * <p>
     * Implements a retry mechanism (max 5 attempts). It blocks the initializing thread
     * using CountDownLatch until both the WebSocket connection and the RSA-AES handshake
     * are successfully completed, or until it times out.
     *
     * @param fullWsUrl The complete WebSocket URL (e.g., "wss://domain.com:443" or "ws://localhost:6969").
     */
    public NetworkClient(String fullWsUrl) {
        log.info("Connecting to server...");

        for (int i = 0; i < 5; i++) {
            try {
                wsClient = new AuctionWSClient(new URI(fullWsUrl));

                // Reset the synchronization latch for each connection attempt
                handshakeLatch = new CountDownLatch(1);

                // Block the thread until the WebSocket is OPEN or fails
                boolean connected = wsClient.connectBlocking();

                if (connected) {
                    // Wait for the AES handshake to finish in the background (Max 3 seconds)
                    boolean isHandshakeDone = handshakeLatch.await(3, TimeUnit.SECONDS);

                    if (isHandshakeDone && isAesKeyEstablished) {
                        log.info("Successfully connected.");

                        // TIME SYNC INITIATION: Ping the server to synchronize clocks securely
                        this.sendMessage("TIME_SYNC", System.currentTimeMillis());

                        return;
                    } else {
                        log.warn("Failed at try {} - Handshake timeout", (i + 1));
                        wsClient.close();
                    }
                } else {
                    // Handle failed connectBlocking() (server offline)
                    log.warn("Failed at try {} - Connection refused", (i + 1));
                }
            } catch (Exception e) {
                log.warn("Failed at try {} - {}", (i+1), e.getMessage());
            }

            if (i < 4) {
                try {
                    Thread.sleep(2000); // Wait 2 seconds before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        log.debug("Failed after 5 tries to connect to {}", fullWsUrl);
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
            log.warn("Cannot send command: \"{}\" due to not being connected.", command);
            return;
        }

        try {
            NetworkMessage msg = new NetworkMessage(command, data);
            String json = mapper.writeValueAsString(msg);
            String encryptedPayload = CryptoUtil.encryptAES(json, myAesKey);

            // Use the non-blocking send method of Java-WebSocket
            wsClient.send(encryptedPayload);

        } catch (Exception e) {
            log.error("JSON package error: {}", e.getMessage());
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
                    log.error("Handshake failed: {}", e.getMessage());
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
                log.warn("Ignore invalid data package: {}", e.getMessage());
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            isAesKeyEstablished = false; // Revoke security clearance
            log.debug("Connection closed. Reason: {}", reason);
        }

        @Override
        public void onError(Exception ex) {
            log.error("{}", ex.getMessage());
        }
    }
}