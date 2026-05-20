package client.service;

import client.network.NetworkService;
import gui.process.AlertHelper;
import javafx.scene.control.Alert;
import model.auction.Auction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class AuctionService {
    private static final Logger log = LoggerFactory.getLogger(AuctionService.class);
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
    /**
     * Sends a request to the server to extend the auction time.
     * Currently a placeholder — implementation pending.
     *
     * @param auctionId    The target auction's ID.
     * @param timeMinutes  Number of minutes to extend by.
     */
    public static void extendAuctionTimeMinutes(String auctionId, long timeMinutes) {
        // TODO: Implement when server-side EXTEND_AUCTION command is ready.
        log.warn("extendAuctionTimeMinutes() called but not yet implemented.");
    }
}
