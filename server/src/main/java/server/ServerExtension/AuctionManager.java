package server.ServerExtension;

import model.Auction;
import java.util.ArrayList;
import java.util.List;

public class AuctionManager {
    private static final List<Auction> auctionList = new ArrayList<>();

    public static synchronized void addAuctionToMonitor(Auction auction) {
        auctionList.add(auction);
    }

    public static List<Auction> getAuctionList() {
        return auctionList;
    }
}