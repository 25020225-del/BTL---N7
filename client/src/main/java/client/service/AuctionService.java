package client.service;

import client.network.NetworkService;
import model.auction.Auction;
import java.util.Map;

/**
 * Client-side service facade orchestrating message dispatches for auction workflows.
 */
public final class AuctionService {

    private AuctionService() {
    }

    public static void placeBid(String auctionId, long bidAmount) {
        Map<String, Object> bidData = Map.of(
                "auctionId", auctionId,
                "bidAmount", bidAmount
        );
        NetworkService.sendMessage("PLACE_BID", bidData);
    }

    public static void fetchAuctions() {
        NetworkService.sendMessage("FETCH_AUCTIONS", "");
    }

    public static void fetchTransactions(String auctionId) {
        NetworkService.sendMessage("FETCH_TRANSACTIONS", auctionId);
    }

    public static void createAuction(Auction auction) {
        NetworkService.sendMessage("CREATE_AUCTION", auction);
    }

    public static void fetchMyAuctions() {
        NetworkService.sendMessage("FETCH_MY_AUCTIONS", "");
    }
    public static void deleteAuction(String auctionId) {
        NetworkService.sendMessage("DELETE_AUCTION", auctionId);
    }
}