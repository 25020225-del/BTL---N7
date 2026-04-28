package client.handler;

import client.network.NetworkClient;
import network.NetworkMessage;
import javafx.application.Platform;
import gui.process.AlertHelper;
import javafx.scene.control.Alert.AlertType;

import java.util.Map;

import static utils.ConsoleColors.*;

public class ClientAuctionHandler implements ResponseHandler {
    @Override
    public void handle(NetworkMessage message, NetworkClient client) {
        String command = message.getCommand();

        if ("UPDATE_AUCTION_PRICE".equals(command)) {
            Map<String, Object> data = (Map<String, Object>) message.getData();
            String auctionId = (String) data.get("auctionId");
            double newPrice = ((Number) data.get("newPrice")).doubleValue();

            Platform.runLater(() -> {
                // Find the product row with the corresponding ID in the UI and update the price label
                // TODO: add a widget search function to the ClientBidderController
                System.out.println("[System]: Auction " + auctionId + " updated its price: " + newPrice);
            });
        }

        if ("CLI_BROADCAST".equals(command)) {
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