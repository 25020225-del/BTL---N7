package client.handler;

import client.network.NetworkClient;
import javafx.application.Platform;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Handles foundational system-level protocol messages including redirect,
 * exit enforcement, and network time synchronization.
 */
public class ClientSystemHandler implements ResponseHandler {

    private static final Logger log = LoggerFactory.getLogger(ClientSystemHandler.class);
    private static final long KICK_EXIT_DELAY_MS = 1_000L;

    @Override
    public void handle(NetworkMessage message, NetworkClient client) throws Exception {
        switch (message.getCommand()) {
            case "REDIRECT" -> handleRedirect(message);
            case "KICKED" -> handleKicked(message, client);
            case "TIME_SYNC_ACK" -> handleTimeSyncAck(message);
            case "GENERAL_ERROR" -> handleGeneralError(message);
        }
    }

    private void handleRedirect(NetworkMessage message) {
        String url = (String) message.getData();
        Platform.runLater(() -> {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            } catch (Exception e) {
                log.error("Redirect failed: {}", e.getMessage());
            }
        });
    }

    private void handleKicked(NetworkMessage message, NetworkClient client) {
        log.warn("Kicked by server: {}", message.getData());
        Thread exitThread = new Thread(() -> {
            try {
                Thread.sleep(KICK_EXIT_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        });
        exitThread.setDaemon(true);
        exitThread.setName("kicked-exit-thread");
        exitThread.start();
    }

    @SuppressWarnings("unchecked")
    private void handleTimeSyncAck(NetworkMessage message) {
        Map<String, Number> syncData = (Map<String, Number>) message.getData();
        long clientSendTime = syncData.get("clientSendTime").longValue();
        long serverTime = syncData.get("serverTime").longValue();
        long clientReceiveTime = System.currentTimeMillis();

        utils.TimeUtil.calibrateOffset(clientSendTime, serverTime, clientReceiveTime);
        log.debug("System clock synchronized.");
    }

    private void handleGeneralError(NetworkMessage message) {
        String errorMessage = client.utils.ErrorParser.parse(message.getData());
        log.warn("Server reported error: {}", errorMessage);
        AuctionEventBus.fireEvent(AuctionEventBus.GENERAL_ERROR, errorMessage);
    }
}