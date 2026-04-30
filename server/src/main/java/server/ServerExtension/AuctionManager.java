package server.ServerExtension;

import model.Auction;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuctionManager {
    private static final List<Auction> auctionList = new CopyOnWriteArrayList<>();

    private static final ConcurrentHashMap<String, Object> auctionLocks = new ConcurrentHashMap<>();

    public static Object getLockForAuction(String auctionId) {
        return auctionLocks.computeIfAbsent(auctionId, k -> new Object());
    }

    public static void removeAuctionLock(String auctionId){
        auctionLocks.remove(auctionId);
    }

    public static synchronized void addAuctionToMonitor(Auction auction) {
        auctionList.add(auction);
    }

    public static List<Auction> getAuctionList() {
        return auctionList;
    }
}