package client.handler;

import client.network.NetworkClient;
import network.NetworkMessage;
import javafx.application.Platform;

import java.awt.Desktop;
import java.net.URI;

import static utils.ConsoleColors.*;

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
            System.out.println(YELLOW + "[System]: You have been kicked. Reason: " + message.getData() + RESET);

            if (client.getOnMessageReceived() != null) {
                Platform.runLater(() -> client.getOnMessageReceived().accept(message));
            }

            Thread.sleep(1000);
            System.exit(0);
        }
    }
}