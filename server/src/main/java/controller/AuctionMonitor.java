package controller;

import model.Auction;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static utils.ConsoleColors.*;

public class AuctionMonitor {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private List<Auction> allAuctions;

    public AuctionMonitor(List<Auction> allAuctions) {
        this.allAuctions = allAuctions;
    }

    public void startMonitoring() {
        System.out.println(GREEN + "[Monitor]: The automatic auction monitoring system has been launched" + RESET);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<Auction> safeSnapshot;
                synchronized (allAuctions) {
                    safeSnapshot = new ArrayList<>(allAuctions);
                }

                for (Auction auction : safeSnapshot) {
                    synchronized (auction) {
                        if (auction.getStatus().equals(Auction.STATUS_RUNNING)) {
                            auction.closeAuctionIfTimeIsUp();
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[Error]: Error during the bidding scan process: " + RED + e.getMessage() + RESET);
                e.printStackTrace();
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    public void stopMonitoring() {
        scheduler.shutdown();
        System.out.println(YELLOW + "[Monitor]: The auction monitoring system has been turned off" + RESET);
    }
}