package client.handler;

import client.network.NetworkClient;
import network.NetworkMessage;
import javafx.application.Platform;

import java.awt.Desktop;
import java.net.URI;
import java.util.Map;

import static utils.ConsoleColors.*;

/**
 * Handles system-level commands received from the server,
 * such as external browser redirections, forced disconnections (kicks),
 * and time synchronization acknowledgments.
 */
public class ClientSystemHandler implements ResponseHandler {
    @Override
    public void handle(NetworkMessage message, NetworkClient client) throws Exception {
        String command = message.getCommand();

        if ("REDIRECT".equals(command)) {
            String url = (String) message.getData();
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                System.out.println("[System]: Redirecting to: " + YELLOW + url + RESET);
            }
        }
        else if ("KICKED".equals(command)) {
            System.out.println("[System]:" + YELLOW + " You have been kicked. Reason: " + message.getData() + RESET);

            // Notify UI if applicable before shutting down
            if (client.getOnMessageReceived() != null) {
                Platform.runLater(() -> client.getOnMessageReceived().accept(message));
            }

            Thread.sleep(1000);
            System.exit(0);
        }
        else if ("TIME_SYNC_ACK".equals(command)) {
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