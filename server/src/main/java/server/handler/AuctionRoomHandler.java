package server.handler;

import network.NetworkMessage;
import server.ClientHandler;
import server.ServerExtension.ClientManager;

import java.util.Map;

public class AuctionRoomHandler implements CommandHandler {
    @Override
    public void handle(NetworkMessage message, ClientHandler client) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) message.getData();
        String auctionId = (String) data.get("auctionId");

        if (auctionId == null || auctionId.isBlank()) {
            client.sendResponse("ERROR", "Missing auctionId");
            return;
        }

        if ("JOIN_AUCTION".equals(message.getCommand())) {
            ClientManager.subscribeToAuction(auctionId, client);
            client.sendResponse("JOIN_AUCTION_SUCCESS", auctionId);
        } else {
            ClientManager.unsubscribeFromAuction(auctionId, client);
            client.sendResponse("LEAVE_AUCTION_SUCCESS", auctionId);
        }
    }
}