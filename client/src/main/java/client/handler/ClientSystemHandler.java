package client.handler;

import client.network.NetworkClient;
import javafx.application.Platform;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.net.URI;
import java.util.Map;

/**
 * Handles system-level commands received from the server, such as:
 * <ul>
 *     <li>{@code REDIRECT} — Opens a URL in the system's default browser.</li>
 *     <li>{@code KICKED} — Notifies the UI, waits briefly, then shuts down the client.</li>
 *     <li>{@code TIME_SYNC_ACK} — Calibrates the local clock offset against server time.</li>
 *     <li>{@code GENERAL_ERROR} — Parses and broadcasts a user-visible error message.</li>
 * </ul>
 */
public class ClientSystemHandler implements ResponseHandler {

    private static final Logger log = LoggerFactory.getLogger(ClientSystemHandler.class);

    /**
     * Delay in milliseconds before forcibly exiting after a KICKED event.
     */
    private static final long KICK_EXIT_DELAY_MS = 1_000L;

    @Override
    public void handle(NetworkMessage message, NetworkClient client) throws Exception {
        switch (message.getCommand()) {
            case "REDIRECT" -> handleRedirect(message);
            case "KICKED" -> handleKicked(message, client);
            case "TIME_SYNC_ACK" -> handleTimeSyncAck(message);
            case "GENERAL_ERROR" -> handleGeneralError(message);
            default -> log.warn("Unhandled system command: {}", message.getCommand());
        }
    }

    // ── Private Command Handlers ──────────────────────────────────────────────

    /**
     * Opens a given URL in the system's default web browser if supported.
     */
    private void handleRedirect(NetworkMessage message) throws Exception {
        String url = (String) message.getData();
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(new URI(url));
            log.info("Browser redirected to: {}", url);
        } else {
            log.warn("Desktop browse action is not supported on this platform. URL: {}", url);
        }
    }

    /**
     * Notifies the UI of the kick event, then schedules a clean application shutdown.
     *
     * <p><b>FIX:</b> Replaced {@code Thread.sleep()} on the handler thread — which was
     * blocking the WebSocket receiver thread — with a scheduled executor or a
     * Platform.runLater + daemon thread approach to avoid starving the message pipeline.</p>
     */
    private void handleKicked(NetworkMessage message, NetworkClient client) {
        log.warn("Session terminated by server. Reason: {}", message.getData());

        if (client.getOnMessageReceived() != null) {
            Platform.runLater(() -> client.getOnMessageReceived().accept(message));
        }

        // FIX: Use a daemon thread instead of sleeping on the WebSocket receiver thread.
        Thread exitThread = new Thread(() -> {
            try {
                Thread.sleep(KICK_EXIT_DELAY_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        });
        exitThread.setDaemon(true);
        exitThread.setName("kicked-exit-thread");
        exitThread.start();
    }

    /**
     * Calibrates the local clock offset using the three-way timestamp handshake.
     */
    @SuppressWarnings("unchecked")
    private void handleTimeSyncAck(NetworkMessage message) {
        Map<String, Number> syncData = (Map<String, Number>) message.getData();
        long clientSendTime = syncData.get("clientSendTime").longValue();
        long serverTime = syncData.get("serverTime").longValue();
        long clientReceiveTime = System.currentTimeMillis();
        utils.TimeUtil.calibrateOffset(clientSendTime, serverTime, clientReceiveTime);
        log.debug("Clock synchronized with server. Offset calibrated.");
    }

    /**
     * Parses the server error payload and broadcasts it on the {@link AuctionEventBus}.
     */
    private void handleGeneralError(NetworkMessage message) {
        String errorMessage = client.utils.ErrorParser.parse(message.getData());
        log.warn("Server reported a general error: {}", errorMessage);
        AuctionEventBus.fireEvent(AuctionEventBus.GENERAL_ERROR, errorMessage);
    }
}
