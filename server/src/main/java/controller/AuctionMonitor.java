package controller;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;

import model.Auction;
import server.ServerExtension.AuctionManager;

import static utils.ConsoleColors.*;

public class AuctionMonitor {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private List<Auction> allAuctions;

    public AuctionMonitor(List<Auction> allAuctions) {
        this.allAuctions = allAuctions;
    }

    public void startMonitoring() {
        System.out.println("[System]:" + GREEN + " Auction monitor has been launched." + RESET);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                for (Auction auction : allAuctions) {
                    if (auction.getStatus().equals(Auction.STATUS_RUNNING)) {
                        auction.closeAuctionIfTimeIsUp();
                    }

                    String status = auction.getStatus();
                    if (status.equals(Auction.STATUS_FINISHED) ||
                        status.equals(Auction.STATUS_CANCELED) ||
                        status.equals(Auction.STATUS_DELETED)) {
                        allAuctions.remove(auction);
                        AuctionManager.removeAuctionLock(auction.getId());
                        System.out.println("[System]: " + BLUE + "Removed auction " + YELLOW + auction.getId() + RESET + " from RAM.");
                    }
                }
            } catch (Exception e) {
                System.out.println("[System](AuctionMonitor): Error occurred during bidding scan process: " + RED + e.getMessage() + RESET);
                e.printStackTrace();
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    public void stopMonitoring() {
        scheduler.shutdown();
        System.out.println("[System]: " + YELLOW + " Auction monitor has been shutdown." + RESET);
    }
}