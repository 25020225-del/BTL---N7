package client.service;

import client.network.NetworkService;
import gui.process.AlertHelper;
import javafx.scene.control.Alert;
import model.auction.Auction;

import java.util.Map;

public class AuctionService {
    public static void placeBid(String currentAuctionId,long bidAmount) {
        Map<String, Object> bidData = Map.of(
                "auctionId", currentAuctionId,
                "bidAmount", bidAmount
        );
        NetworkService.sendMessage("PLACE_BID",bidData);
    }
    public static void setAutoBid(String currentAuctionId, long maxBid, long bidIncrement) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("auctionId", currentAuctionId);
        payload.put("maxBid", maxBid);
        payload.put("increment", bidIncrement);
        NetworkService.sendMessage("SETUP_AUTOBID", payload);
    }
    public static void deleteAuction(String itemId) {
        NetworkService.sendMessage("DELETE_AUCTION",itemId);
    }
    public static void fetchAuctions() {
        NetworkService.sendMessage("FETCH_AUCTIONS", "");
    }
    public static void fetchTransactions (String  auctionId) {
        NetworkService.sendMessage("FETCH_TRANSACTIONS",auctionId);
    }
    public static void createAuction(Auction auction) {
        NetworkService.sendMessage("CREATE_AUCTION", auction);
    }
    public static void extendAuctionTimeMinutes(String auctionId, long timeMinutes) {
        AlertHelper.showAlert(Alert.AlertType.INFORMATION,"gay","cum shot");
    }
}
