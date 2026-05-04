package controller;

import database.DatabaseManager;
import database.TransactionManager;
import database.dao.AuctionDAO;
import model.auction.Auction;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static utils.ConsoleColors.*;

/**
 * A background daemon service that continuously monitors active auctions.
 * It manages real-time expiration in RAM and routinely sweeps the database
 * to clean up any "orphaned" or "ghost" auctions left over from previous server sessions.
 */
public class AuctionMonitor {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private List<Auction> allAuctions;
    private final AuctionDAO auctionDAO = new AuctionDAO(); // Instantiate AuctionDAO

    /**
     * Constructs the monitor with a reference to the global active auction list in RAM.
     *
     * @param allAuctions The shared list of currently monitored auctions.
     */
    public AuctionMonitor(List<Auction> allAuctions) {
        this.allAuctions = allAuctions;
    }

    /**
     * Starts the scheduled background task.
     * The task runs periodically to check if any auction has exceeded its end time,
     * handling both volatile RAM instances and persistent Database records.
     */
    public void startMonitoring() {
        System.out.println("[System]:" + GREEN + " Auction monitor has been launched." + RESET);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 1. Process active auctions currently held in Server RAM
                processRamAuctions();

                // 2. Sweep the database for any orphaned/ghost auctions (e.g., from prior server crashes)
                sweepDatabaseForOrphans();

            } catch (Exception e) {
                System.out.println("[System](AuctionMonitor): Error occurred during bidding scan process: " + RED + e.getMessage() + RESET);
                e.printStackTrace();
            }
        }, 0, 10, TimeUnit.SECONDS); // Scans every 10 seconds
    }

    /**
     * Iterates through the in-memory auction list, finalizing those whose time has expired.
     */
    private void processRamAuctions() {
        // Use a safe copy to iterate, preventing ConcurrentModificationException
        for (Auction auction : List.copyOf(allAuctions)) {

            // Automatically start auctions that are OPEN and have reached their start time.
            if (auction.getStatus().equals(Auction.STATUS_OPEN) && LocalDateTime.now().isAfter(auction.getStartTime())) {
                // Ensure the auction has not already ended
                if (LocalDateTime.now().isBefore(auction.getEndTime())) {
                    auction.setStatus(Auction.STATUS_RUNNING);
                    System.out.println("[System]: Auction " + YELLOW + auction.getId() + RESET + " has started and is now " + GREEN + "RUNNING." + RESET);

                    // Asynchronously update the database to persist the new state
                    Callable<Boolean> dbUpdateTask = () -> {
                        try {
                            return auctionDAO.updateAuctionStatus(auction.getId(), Auction.STATUS_RUNNING);
                        } catch (Exception e) {
                            System.out.println("[Database]: Failed to update auction to RUNNING: " + RED + e.getMessage() + RESET);
                            return false;
                        }
                    };
                    TransactionManager.submitTask(dbUpdateTask);
                }
            }

            // Check both RUNNING and OPEN statuses to handle auctions with zero bids
            if (auction.getStatus().equals(Auction.STATUS_RUNNING) || auction.getStatus().equals(Auction.STATUS_OPEN)) {
                auction.closeAuctionIfTimeIsUp();
            }

            String status = auction.getStatus();
            if (status.equals(Auction.STATUS_FINISHED) ||
                    status.equals(Auction.STATUS_CANCELED) ||
                    status.equals(Auction.STATUS_DELETED)) {

                // Remove from Server RAM to prevent memory leaks
                allAuctions.remove(auction);
                AuctionManager.removeAuctionLock(auction.getId());

                // Persist the closed status to the SQLite Database asynchronously
                Callable<Boolean> dbUpdateTask = () -> {
                    try {
                        return auctionDAO.updateAuctionStatus(auction.getId(), status);
                    } catch (Exception e) {
                        return false;
                    }
                };
                TransactionManager.submitTask(dbUpdateTask);

                // Broadcast removal command to all connected clients
                ClientManager.broadcast("REMOVE_AUCTION", auction.getId(), null);

                System.out.println("[System]: " + BLUE + "Removed auction " + YELLOW + auction.getId() + RESET + " from RAM and updated DB to " + status);
            }
        }
    }

    /**
     * Sweeps and finalizes finished auctions.
     * Executes financial settlements: refunds excess locked funds to the winner
     * and transfers the final closing price to the seller's wallet.
     */
    private void processFinancialSettlement(Auction auction) {
        // Viết logic cộng tiền (currentPrice) cho auction.getSeller()
        // Viết logic hoàn tiền (highestMaxBid - currentPrice) cho auction.getWinningBidder()
        // Thông qua TransactionManager để đảm bảo tính ACID
    }

    /**
     * Scans the database directly to find and close any auctions that expired
     * but were not loaded into RAM (e.g., created before a recent server restart).
     */
    private void sweepDatabaseForOrphans() {
        Callable<Boolean> dbSweepTask = () -> {
            try {
                List<String> updatedIds = auctionDAO.sweepOrphanAuctions();
                for (String id : updatedIds) {
                    // Force clients to remove the ghost item from their UI
                    ClientManager.broadcast("REMOVE_AUCTION", id, null);
                }
                return true;
            } catch (Exception e) {
                System.out.println("[Database]: Orphan sweep error: " + RED + e.getMessage() + RESET);
                return false;
            }
        };

        // Push the sweeping task to the database thread queue
        TransactionManager.submitTask(dbSweepTask);
    }

    /**
     * Gracefully shuts down the monitoring daemon service.
     */
    public void stopMonitoring() {
        scheduler.shutdown();
        System.out.println("[System]: " + YELLOW + " Auction monitor has been shutdown." + RESET);
    }
}
