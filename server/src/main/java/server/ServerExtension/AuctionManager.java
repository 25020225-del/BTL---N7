package server.ServerExtension;

import model.auction.Auction;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * RAM-based shared infrastructure registry managing active in-memory auctions.
 * Exposes fine-grained striped lock synchronization constructs to isolate heavy write contentions.
 */
public class AuctionManager {

    private static final List<Auction> auctionList = new CopyOnWriteArrayList<>();
    private static final ConcurrentHashMap<String, Object> auctionLocks = new ConcurrentHashMap<>();

    /**
     * Allocates or extracts a localized synchronization monitor lock object assigned to a specific auction ID.
     * Maps processing contentions concurrently across discrete tracking models.
     *
     * @param auctionId unique identity key targeting an auction resource
     * @return a localized dedicated synchronization monitor object
     */
    public static Object getLockForAuction(String auctionId) {
        return auctionLocks.computeIfAbsent(auctionId, k -> new Object());
    }

    /**
     * Clears tracking lock contexts matching the targeted identifier code to bypass memory leaks.
     */
    public static void removeAuctionLock(String auctionId) {
        auctionLocks.remove(auctionId);
    }

    public static synchronized void addAuctionToMonitor(Auction auction) {
        auctionList.add(auction);
    }

    public static List<Auction> getAuctionList() {
        return auctionList;
    }
}