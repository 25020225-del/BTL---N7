package client.handler;

import client.network.NetworkClient;
import javafx.application.Platform;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Central dispatcher that routes incoming JSON commands from the server
 * to their appropriate dedicated handler classes.
 */
public class ResponseDispatcher {
    private static final Logger log = LoggerFactory.getLogger(ResponseDispatcher.class);

    private final Map<String, ResponseHandler> handlers = new HashMap<>();

    public ResponseDispatcher() {
        registerHandlers();
    }

    private void registerHandlers() {
        ClientSystemHandler systemHandler = new ClientSystemHandler();
        handlers.put("REDIRECT", systemHandler);
        handlers.put("KICKED", systemHandler);
        handlers.put("TIME_SYNC_ACK", systemHandler);

        ClientAuctionHandler auctionHandler = new ClientAuctionHandler();
        handlers.put("CLI_BROADCAST", auctionHandler);
        handlers.put("CREATE_SUCCESS", auctionHandler);
        handlers.put("CHAT", auctionHandler);

        ClientPaymentHandler paymentHandler = new ClientPaymentHandler();
        handlers.put("PAYMENT_REDIRECT", paymentHandler);
        handlers.put("DEPOSIT_SUCCESS", paymentHandler);
    }

    public void dispatch(NetworkMessage message, NetworkClient client) {
        String command = message.getCommand();
        ResponseHandler handler = handlers.get(command);

        if (handler != null) {
            try {
                handler.handle(message, client);
            } catch (Exception e) {
                log.error("Command \"{}\": {}", command, e.getMessage());
            }
        } else {
            // Unhandled commands are forwarded directly to the UI component if a callback is registered
            if (client.getOnMessageReceived() != null) {
                Platform.runLater(() -> client.getOnMessageReceived().accept(message));
            }
        }
    }
}