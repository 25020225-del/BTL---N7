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
 * Handles system-level commands received from the server,
 * such as external browser redirections, forced disconnections (kicks),
 * and time synchronization acknowledgments.
 */
public class ClientSystemHandler implements ResponseHandler {
    private static final Logger log = LoggerFactory.getLogger(ClientSystemHandler.class);

    @Override
    public void handle(NetworkMessage message, NetworkClient client) throws Exception {
        String command = message.getCommand();

        if ("REDIRECT".equals(command)) {
            String url = (String) message.getData();
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                log.info("Redirecting to {}", url);
            }
        } else if ("KICKED".equals(command)) {
            log.warn("Kicked. Reason: {}", message.getData());

            // Notify UI if applicable before shutting down
            if (client.getOnMessageReceived() != null) {
                Platform.runLater(() -> client.getOnMessageReceived().accept(message));
            }

            Thread.sleep(1000);
            System.exit(0);
        } else if ("TIME_SYNC_ACK".equals(command)) {
            // Process the time synchronization response from the server
            @SuppressWarnings("unchecked")
            Map<String, Number> syncData = (Map<String, Number>) message.getData();

            // Extract the timestamps safely
            long clientSendTime = syncData.get("clientSendTime").longValue();
            long serverTime = syncData.get("serverTime").longValue();
            long clientReceiveTime = System.currentTimeMillis();

            // Calibrate the global time offset
            utils.TimeUtil.calibrateOffset(clientSendTime, serverTime, clientReceiveTime);
        }
    }
}