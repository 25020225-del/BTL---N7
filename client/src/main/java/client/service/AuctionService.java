package client.service;

import client.network.NetworkService;
import model.auction.Auction;

import java.util.HashMap;
import java.util.Map;

/**
 * Service class encapsulating all auction-related network commands sent from the client.
 *
 * <p>Acts as a thin facade over {@link NetworkService}, providing a strongly-typed API
 * for auction operations. This keeps raw command strings isolated in one place.</p>
 *
 * <p>This is a stateless utility class and must not be instantiated.</p>
 */
public final class AuctionService {

    /**
     * Private constructor — utility class, not instantiable.
     */
    private AuctionService() {
    }

    /**
     * Sends a bid placement request to the server.
     *
     * @param auctionId The unique identifier of the auction to bid on.
     * @param bidAmount The amount being bid in VND.
     */
    public static void placeBid(String auctionId, long bidAmount) {
        Map<String, Object> bidData = Map.of(
                "auctionId", auctionId,
                "bidAmount", bidAmount
        );
        NetworkService.sendMessage("PLACE_BID", bidData);
    }

    /**
     * Sends a request to configure automatic bidding for an auction.
     *
     * @param auctionId    The unique identifier of the auction.
     * @param maxBid       The maximum price the auto-bidder will not exceed.
     * @param bidIncrement The fixed step size for each automatic bid.
     */
    public static void setAutoBid(String auctionId, long maxBid, long bidIncrement) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("auctionId", auctionId);
        payload.put("maxBid", maxBid);
        payload.put("increment", bidIncrement);
        NetworkService.sendMessage("SETUP_AUTOBID", payload);
    }

    /**
     * Sends a request to delete (cancel) an existing auction.
     *
     * @param auctionId The unique identifier of the auction to delete.
     */
    public static void deleteAuction(String auctionId) {
        NetworkService.sendMessage("DELETE_AUCTION", auctionId);
    }

    /**
     * Requests the server to return all available auction listings.
     */
    public static void fetchAuctions() {
        NetworkService.sendMessage("FETCH_AUCTIONS", "");
    }

    /**
     * Requests the bid transaction history for a specific auction.
     *
     * @param auctionId The unique identifier of the auction whose history is requested.
     */
    public static void fetchTransactions(String auctionId) {
        NetworkService.sendMessage("FETCH_TRANSACTIONS", auctionId);
    }

    /**
     * Sends a new auction creation request to the server.
     *
     * @param auction The fully populated {@link Auction} domain object to create.
     */
    public static void createAuction(Auction auction) {
        NetworkService.sendMessage("CREATE_AUCTION", auction);
    }

    /**
     * Sends a request to extend the remaining time of an active auction.
     *
     * @param auctionId   The unique identifier of the auction to extend.
     * @param timeMinutes The number of minutes to add to the auction's remaining time.
     */
    public static void extendAuctionTimeMinutes(String auctionId, long timeMinutes) {
        // FIX: Removed inappropriate placeholder alert. Implemented proper server call.
        Map<String, Object> payload = Map.of(
                "auctionId", auctionId,
                "timeMinutes", timeMinutes
        );
        NetworkService.sendMessage("EXTEND_AUCTION_TIME", payload);
    }

    public static void fetchMyAuctions() {
        NetworkService.sendMessage("FETCH_MY_AUCTIONS", "");
    }
}
