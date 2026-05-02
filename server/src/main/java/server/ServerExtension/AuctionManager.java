package server.ServerExtension;

import model.auction.Auction;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the collection of active auctions and provides synchronization mechanisms.
 * This class serves as the central RAM-based registry for auctions currently
 * being monitored by the server. It utilizes thread-safe collections and a
 * fine-grained locking strategy to ensure data integrity during high-concurrency bidding.
 */
public class AuctionManager {

    /**
     * A thread-safe list containing all auctions currently active in the system.
     * Uses {@link CopyOnWriteArrayList} for safe iteration during broadcasts.
     */
    private static final List<Auction> auctionList = new CopyOnWriteArrayList<>();

    /**
     * A map providing unique lock objects for each auction ID.
     * This enables "Striped Locking," allowing different auctions to be
     * processed simultaneously without blocking each other.
     */
    private static final ConcurrentHashMap<String, Object> auctionLocks = new ConcurrentHashMap<>();

    /**
     * Retrieves or creates a unique monitor object (lock) for a specific auction.
     * This ensures that operations on a single auction (like placing a bid)
     * are synchronized, while operations on different auctions remain concurrent.
     *
     * @param auctionId The unique identifier of the auction.
     * @return A lock object dedicated to the specified auction.
     */
    public static Object getLockForAuction(String auctionId) {
        return auctionLocks.computeIfAbsent(auctionId, k -> new Object());
    }

    /**
     * Removes the lock object associated with an auction.
     * Typically called when an auction is finished or deleted to prevent memory leaks.
     *
     * @param auctionId The unique identifier of the auction to clean up.
     */
    public static void removeAuctionLock(String auctionId){
        auctionLocks.remove(auctionId);
    }

    /**
     * Adds a new auction to the monitoring pool.
     * This method is synchronized to ensure atomic additions to the registry.
     *
     * @param auction The auction instance to be tracked by the server.
     */
    public static synchronized void addAuctionToMonitor(Auction auction) {
        auctionList.add(auction);
    }

    /**
     * Returns the global list of auctions currently managed in RAM.
     *
     * @return A thread-safe list of active {@link Auction} objects.
     */
    public static List<Auction> getAuctionList() {
        return auctionList;
    }
}