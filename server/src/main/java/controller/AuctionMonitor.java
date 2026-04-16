package controller;

import model.Auction;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AuctionMonitor {

    public static final String ANSI_RESET  = "\u001B[0m";
    public static final String ANSI_RED    = "\u001B[31m";
    public static final String ANSI_GREEN  = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private List<Auction> allAuctions;

    public AuctionMonitor(List<Auction> allAuctions) {this.allAuctions = allAuctions;}

    public void startMonitoring() {
        System.out.println(ANSI_GREEN + "[Monitor]: The automatic auction monitoring system has been launched" + ANSI_RESET);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                for (Auction auction : allAuctions) {
                    if (auction.getStatus().equals(Auction.STATUS_RUNNING)) {
                        auction.closeAuctionIfTimeIsUp();
                    }
                }
            } catch (Exception e) {
                System.err.println("[Error]: Error during the bidding scan process: " + ANSI_RED + e.getMessage() + ANSI_RESET);
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    public void stopMonitoring() {
        scheduler.shutdown();
        System.out.println(ANSI_YELLOW + "[Monitor]: The auction monitoring system has been turned off" + ANSI_RESET);
    }

}