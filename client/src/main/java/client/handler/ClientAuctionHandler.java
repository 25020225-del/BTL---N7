package client.handler;

import client.network.NetworkClient;
import gui.process.AlertHelper;
import javafx.application.Platform;
import javafx.scene.control.Alert.AlertType;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static utils.ConsoleColors.*;

/**
 * Handles auction-related responses and real-time updates broadcasted by the server.
 * This handler processes incoming price changes, global broadcasts, and success notifications.
 */
public class ClientAuctionHandler implements ResponseHandler {
    private static final Logger log = LoggerFactory.getLogger(ClientAuctionHandler.class);

    /**
     * Dispatches the incoming network message to the appropriate UI update logic.
     *
     * @param message The network message containing the command and payload.
     * @param client  The active network client session.
     */
    @Override
    public void handle(NetworkMessage message, NetworkClient client) {
        String command = message.getCommand();

        if ("UPDATE_AUCTION_PRICE".equals(command)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) message.getData();

            String auctionId = (String) data.get("auctionId");
            double newPrice = ((Number) data.get("newPrice")).doubleValue();

            log.info("Auction {} updated its price: {}", auctionId, newPrice);

            // Fire the event through the bus to decouple network logic from UI controllers
            AuctionEventBus.fireEvent(AuctionEventBus.PRICE_UPDATED, data);

        } else if ("CLI_BROADCAST".equals(command)) {
            log.info(message.getData().toString());
        } else if ("CREATE_SUCCESS".equals(command)) {
            log.info(message.getData().toString());
            Platform.runLater(() -> {
                AlertHelper.showAlert(AlertType.INFORMATION, "Success", message.getData().toString());
            });
        } else if ("CHAT".equals(command)) {
            log.info(message.getData().toString());
        }
    }
}