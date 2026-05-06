package server.handler;

import controller.ServerSellerController;
import model.user.User;
import network.NetworkMessage;
import server.ClientHandler;
import server.ServerExtension.AuctionManager;
import model.auction.Auction;

import java.util.Map;

/**
 * Handles seller-specific commands such as editing or deleting an auction.
 */
public class SellerActionHandler implements CommandHandler {

    private final ServerSellerController sellerCtrl;
    private final database.dao.AuctionDAO auctionDAO;

    public SellerActionHandler(ServerSellerController sellerCtrl, database.dao.AuctionDAO auctionDAO) {
        this.sellerCtrl = sellerCtrl;
        this.auctionDAO = auctionDAO;
    }

    @Override
    public void handle(NetworkMessage message, ClientHandler client) {
        User user = client.getUser();
        if (user == null) {
            client.sendResponse("ERROR", "You must be logged in.");
            return;
        }

        String command = message.getCommand();
        if ("EDIT_AUCTION".equals(command)) {
            handleEdit(message.getData(), client);
        } else if ("DELETE_AUCTION".equals(command)) {
            handleDelete(message.getData(), client);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleEdit(Object data, ClientHandler client) {
        Map<String, Object> payload = (Map<String, Object>) data;
        String auctionId = (String) payload.get("auctionId");
        
        Auction auction = null;
        try {
            auction = auctionDAO.getAuctionById(auctionId);
            if (auction != null) {
                for (Auction ramAuction : AuctionManager.getAuctionList()) {
                    if (ramAuction.getId().equals(auctionId)) {
                        auction = ramAuction;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            client.sendResponse("ERROR", "Database error: " + e.getMessage());
            return;
        }

        if (auction == null) {
            client.sendResponse("ERROR", "Auction not found.");
            return;
        }

        // Get new values from payload or keep existing ones
        String newName = payload.containsKey("itemName") ? (String) payload.get("itemName") : auction.getItem().getItemName();
        String newDesc = payload.containsKey("description") ? (String) payload.get("description") : auction.getItem().getDescription();
        long newStartPrice = payload.containsKey("startPrice") ? Long.parseLong(payload.get("startPrice").toString()) : auction.getItem().getStartingPrice();
        
        java.time.LocalDateTime newStartTime = auction.getStartTime();
        if (payload.containsKey("newStartTime")) {
            newStartTime = java.time.LocalDateTime.parse((String) payload.get("newStartTime"));
        }
        
        java.time.LocalDateTime newEndTime = auction.getEndTime();
        if (payload.containsKey("newEndTime")) {
            newEndTime = java.time.LocalDateTime.parse((String) payload.get("newEndTime"));
        }

        boolean success = sellerCtrl.editAuction(
                client.getUser(),
                auction,
                newName,
                newDesc,
                newStartPrice,
                newStartTime,
                newEndTime
        );

        if (success) {
            client.sendResponse("EDIT_SUCCESS", "Auction updated successfully.");
        } else {
            client.sendResponse("ERROR", "Failed to edit auction. Ensure it hasn't started yet.");
        }
    }

    private void handleDelete(Object data, ClientHandler client) {
        String auctionId = (String) data;
        Auction auction = null;
        
        try {
            // 1. Get from Database
            auction = auctionDAO.getAuctionById(auctionId);
            
            if (auction != null) {
                // 2. Cross-reference with RAM
                for (Auction ramAuction : AuctionManager.getAuctionList()) {
                    if (ramAuction.getId().equals(auctionId)) {
                        auction = ramAuction;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            client.sendResponse("ERROR", "Database error: " + e.getMessage());
            return;
        }
        
        if (auction == null) {
            client.sendResponse("ERROR", "Auction not found.");
            return;
        }

        boolean success = sellerCtrl.deleteAuction(client.getUser(), auction);
        if (success) {
            client.sendResponse("DELETE_SUCCESS", "Auction deleted successfully.");
        } else {
            client.sendResponse("ERROR", "Failed to delete auction.");
        }
    }
}
