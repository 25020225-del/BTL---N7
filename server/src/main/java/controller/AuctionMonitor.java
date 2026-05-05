package controller;

import database.DatabaseManager;
import database.TransactionManager;
import database.dao.AuctionDAO;
import database.dao.WalletDAO;
import model.auction.Auction;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;

import java.sql.Connection;
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
    private final WalletDAO walletDAO = new WalletDAO(); // Instantiate WalletDAO

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

            // Apply Striped Locking to ensure the daemon thread doesn't clash with incoming
            // bids that are concurrently modifying or reading the auction's state.
            synchronized (server.ServerExtension.AuctionManager.getLockForAuction(auction.getId())) {

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

                    // Trigger financial settlement if the auction finished successfully
                    if (status.equals(Auction.STATUS_FINISHED)) {
                        processFinancialSettlement(auction);
                    }

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
    }

    /**
     * Sweeps and finalizes finished auctions.
     * Executes financial settlements: refunds excess locked funds to the winner
     * and transfers the final closing price to the seller's wallet.
     */
    private void processFinancialSettlement(Auction auction) {
        if (auction.getWinningBidder() == null) {
            return; // No winner, no settlement needed
        }

        Callable<Boolean> settlementTask = () -> {
            String now = LocalDateTime.now().toString();

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false); // Start ACID transaction

                try {
                    // 1. Pay the seller the final auction price
                    double sellerPayment = auction.getCurrentPrice();
                    walletDAO.updateBalance(conn, auction.getSeller().getId(), sellerPayment);

                    walletDAO.addTransaction(
                            conn,
                            "W-IN-" + System.currentTimeMillis(),
                            auction.getSeller().getId(),
                            sellerPayment,
                            "Payment received for completed auction: " + auction.getId(),
                            now
                    );

                    // 2. Refund the winning bidder for the excess locked amount
                    double refundAmount = auction.getHighestMaxBid() - auction.getCurrentPrice();
                    if (refundAmount > 0) {
                        walletDAO.updateBalance(conn, auction.getWinningBidder().getId(), refundAmount);

                        walletDAO.addTransaction(
                                conn,
                                "W-REF-" + (System.currentTimeMillis() + 1), // +1 to ensure unique ID
                                auction.getWinningBidder().getId(),
                                refundAmount,
                                "Refund for excess max bid on auction: " + auction.getId(),
                                now
                        );
                    }

                    conn.commit(); // Finalize changes
                    System.out.println("[System]: Financial settlement completed for auction " + YELLOW + auction.getId() + RESET);
                    return true;

                } catch (Exception e) {
                    conn.rollback(); // Rollback if any error occurs
                    System.out.println("[Database]: Error during financial settlement: " + RED + e.getMessage() + RESET);
                    return false;
                }
            } catch (Exception e) {
                System.out.println("[Database]: Connection error during settlement: " + RED + e.getMessage() + RESET);
                return false;
            }
        };

        TransactionManager.submitTask(settlementTask);
    }

    /**
     * Scans the database directly to find and close any auctions that expired
     * but were not loaded into RAM (e.g., created before a recent server restart).
     */
    private void sweepDatabaseForOrphans() {
        Callable<Boolean> dbSweepTask = () -> {
            try {
                List<Auction> finishedAuctions = auctionDAO.sweepOrphanAuctions();
                for (Auction auction : finishedAuctions) {
                    // Process financial settlement for each orphaned auction that finished
                    processFinancialSettlement(auction);
                    
                    // Force clients to remove the ghost item from their UI
                    ClientManager.broadcast("REMOVE_AUCTION", auction.getId(), null);
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