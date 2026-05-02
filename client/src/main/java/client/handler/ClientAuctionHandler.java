package client.handler;

import client.network.NetworkClient;
import network.NetworkMessage;
import javafx.application.Platform;
import gui.process.AlertHelper;
import javafx.scene.control.Alert.AlertType;

import java.util.Map;

import static utils.ConsoleColors.*;

/**
 * Handles auction-related responses and real-time updates broadcasted by the server.
 * This handler processes incoming price changes, global broadcasts, and success notifications.
 */
public class ClientAuctionHandler implements ResponseHandler {

    // A static reference to the currently active detail controller.
    // This allows the network thread to push real-time updates directly to the active chart.
    public static gui.ItemDetailController activeDetailController = null;

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
            String winnerName = (String) data.get("winnerName");

            Platform.runLater(() -> {
                System.out.println("[System]: Auction " + auctionId + " updated its price: " + newPrice);

                // If the user is currently viewing this specific auction's detail page,
                // trigger the real-time line chart and UI update.
                if (activeDetailController != null) {
                    activeDetailController.updateRealTimePrice(newPrice, winnerName);
                }
            });
        }

        else if ("CLI_BROADCAST".equals(command)) {
            System.out.println(YELLOW + message.getData().toString() + RESET);
        }

        else if ("CREATE_SUCCESS".equals(command)) {
            System.out.println("[System]: " + GREEN + message.getData() + RESET);
            Platform.runLater(() -> {
                AlertHelper.showAlert(AlertType.INFORMATION, "Success", message.getData().toString());
            });
        }

        else if ("CHAT".equals(command)) {
            System.out.println(message.getData());
        }
    }
}