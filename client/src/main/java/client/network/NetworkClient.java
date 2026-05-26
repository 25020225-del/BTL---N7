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
 * Core network infrastructure component managing a persistent full-duplex WebSocket transport channel.
 * Responsible for managing the lifecycle of the connection, enforcing an asymmetric-symmetric hybrid
 * cryptographic handshake contract, and delegating verified network frames to the ingress dispatcher.
 */
public class NetworkClient {
    private static final Logger log = LoggerFactory.getLogger(NetworkClient.class);

    private final String fullWsUrl;
    private final ObjectMapper mapper = JacksonConfig.mapper();
    private final ResponseDispatcher dispatcher = new ResponseDispatcher();

    private AuctionWSClient wsClient;
    private Consumer<NetworkMessage> onMessageReceived;
    private SecretKey myAesKey;
    private boolean isAesKeyEstablished = false;
    private CountDownLatch handshakeLatch;

    /**
     * Allocates a network client bound to a specific target network endpoint.
     * Allocation does not initiate network I/O operations.
     *
     * @param fullWsUrl the target remote WebSocket URI string
     */
    public NetworkClient(String fullWsUrl) {
        this.fullWsUrl = fullWsUrl;
    }

    /**
     * Executes the connection orchestration sequence.
     * Initiates up to 5 blocking synchronization attempts to establish the base transport layer
     * and complete the hybrid cryptographic verification contract before timing out.
     *
     * @return true if the transport layer is established and the security contract is synchronized, false otherwise.
     */
    public boolean connect() {
        log.info("Initiating connection sequence to endpoint: {}", fullWsUrl);

        for (int i = 0; i < 5; i++) {
            try {
                wsClient = new AuctionWSClient(new URI(fullWsUrl));
                handshakeLatch = new CountDownLatch(1);

                if (wsClient.connectBlocking()) {
                    if (handshakeLatch.await(3, TimeUnit.SECONDS) && isAesKeyEstablished) {
                        log.info("Secure transport channel verified and established.");
                        this.sendMessage("TIME_SYNC", System.currentTimeMillis());
                        return true;
                    }
                    log.warn("Cryptographic alignment handshake timed out on attempt {}", (i + 1));
                    wsClient.close();
                } else {
                    log.warn("Transport connection refused on attempt {}", (i + 1));
                }
            } catch (Exception e) {
                log.warn("Network transport allocation failed on attempt {}: {}", (i + 1), e.getMessage());
            }

            if (i < 4) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.error("Failed to establish a secure transport channel after 5 attempts to {}", fullWsUrl);
        return false;
    }

    /**
     * Verifies if the network transport channel is alive and the cryptographic state matches requirements.
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
     * Serializes domain data payloads, wraps them in standard network envelopes,
     * applies symmetric encryption, and puts them into the transport pipeline.
     *
     * @param command the target system operation code
     * @param data    the raw domain data to serialize and secure
     */
    public void sendMessage(String command, Object data) {
        if (!isConnected()) {
            log.warn("Outbound dispatch dropped: Transport channel is unsecured or disconnected for command: {}", command);
            return;
        }

        try {
            NetworkMessage msg = new NetworkMessage(command, data);
            String json = mapper.writeValueAsString(msg);
            String encryptedPayload = CryptoUtil.encryptAES(json, myAesKey);
            wsClient.send(encryptedPayload);
        } catch (Exception e) {
            log.error("Payload packaging or serialization pipeline failure: {}", e.getMessage());
        }
    }

    public String getServerAddress() {
        if (wsClient != null && wsClient.getRemoteSocketAddress() != null) {
            return wsClient.getRemoteSocketAddress().getHostString() + ":" + wsClient.getRemoteSocketAddress().getPort();
        }
        return "Disconnected";
    }

    private class AuctionWSClient extends WebSocketClient {

        public AuctionWSClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
        }

        @Override
        public void onMessage(String message) {
            if (!isAesKeyEstablished) {
                try {
                    PublicKey serverPublicKey = CryptoUtil.getPublicKeyFromBase64(message);
                    myAesKey = CryptoUtil.generateAESKey();
                    String encryptedAesKey = CryptoUtil.encryptAESKeyWithRSA(myAesKey, serverPublicKey);
                    this.send(encryptedAesKey);
                    isAesKeyEstablished = true;
                } catch (Exception e) {
                    log.error("Security handshake negotiation failed: {}", e.getMessage());
                } finally {
                    if (handshakeLatch != null) {
                        handshakeLatch.countDown();
                    }
                }
                return;
            }

            try {
                String jsonMessage = CryptoUtil.decryptAES(message, myAesKey);
                NetworkMessage response = mapper.readValue(jsonMessage, NetworkMessage.class);
                Consumer<NetworkMessage> transientCallback = getOnMessageReceived();
                if (transientCallback != null) {
                    String cmd = response.getCommand();
                    if ("LOGIN_SUCCESS".equals(cmd) || "REQUIRE_2FA".equals(cmd) ||
                            "VERIFY_2FA_SUCCESS".equals(cmd) || "LOGIN_FAIL".equals(cmd) || "ERROR".equals(cmd)) {
                        transientCallback.accept(response);
                        return;
                    }
                }
                dispatcher.dispatch(response, NetworkClient.this);
            } catch (Exception e) {
                log.warn("Ingress pipeline rejected corrupted or untrusted packet: {}", e.getMessage());
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            isAesKeyEstablished = false;
            log.debug("Network connection terminated by {} endpoint. Reason: {}", remote ? "remote" : "local", reason);
        }

        @Override
        public void onError(Exception ex) {
            log.error("Transport layer exception thrown: {}", ex.getMessage());
        }
    }
}