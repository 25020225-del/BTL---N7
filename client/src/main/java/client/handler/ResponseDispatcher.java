package client.handler;

import client.network.NetworkClient;
import network.NetworkMessage;
import javafx.application.Platform;
import java.util.HashMap;
import java.util.Map;

import static utils.ConsoleColors.*;

public class ResponseDispatcher {
    private final Map<String, ResponseHandler> handlers = new HashMap<>();

    public ResponseDispatcher() {
        registerHandlers();
    }

    private void registerHandlers() {
        ClientSystemHandler systemHandler = new ClientSystemHandler();
        handlers.put("REDIRECT", systemHandler);
        handlers.put("KICKED", systemHandler);

        ClientAuctionHandler auctionHandler = new ClientAuctionHandler();
        handlers.put("CLI_BROADCAST", auctionHandler);
        handlers.put("CREATE_SUCCESS", auctionHandler);
        handlers.put("CHAT", auctionHandler);
    }

    public void dispatch(NetworkMessage message, NetworkClient client) {
        String command = message.getCommand();
        ResponseHandler handler = handlers.get(command);

        if (handler != null) {
            try {
                handler.handle(message, client);
            } catch (Exception e) {
                System.out.println("[Error]: Client Dispatcher error for " + command + ": " + RED + e.getMessage() + RESET);
            }
        } else {
            if (client.getOnMessageReceived() != null) {
                Platform.runLater(() -> client.getOnMessageReceived().accept(message));
            }
        }
    }
}