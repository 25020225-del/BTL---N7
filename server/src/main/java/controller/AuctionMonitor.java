package controller;

import database.DatabaseManager;
import database.TransactionManager;
import database.dao.AuctionDAO;
import database.dao.WalletDAO;
import model.auction.Auction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A background daemon service that continuously monitors active auctions.
 * It manages real-time expiration in RAM and routinely sweeps the database
 * to clean up any "orphaned" or "ghost" auctions left over from previous server sessions.
 */
public class AuctionMonitor {
    private static final Logger log = LoggerFactory.getLogger(AuctionMonitor.class);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private List<Auction> allAuctions;
    private final AuctionDAO auctionDAO;
    private final WalletDAO walletDAO;

    /**
     * Constructs the monitor with its dependencies.
     *
     * @param allAuctions The shared list of currently monitored auctions.
     * @param auctionDAO  The DAO for auction persistence.
     * @param walletDAO   The DAO for financial settlements.
     */
    public AuctionMonitor(List<Auction> allAuctions, AuctionDAO auctionDAO, WalletDAO walletDAO) {
        this.allAuctions = allAuctions;
        this.auctionDAO = auctionDAO;
        this.walletDAO = walletDAO;
    }

    /**
     * Starts the scheduled background task.
     * The task runs periodically to check if any auction has exceeded its end time,
     * handling both volatile RAM instances and persistent Database records.
     */
    public void startMonitoring() {
        log.info("Auction Monitor launched.");

        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 1. Process active auctions currently held in Server RAM
                processRamAuctions();

                // 2. Sweep the database for any orphaned/ghost auctions (e.g., from prior server crashes)
                sweepDatabaseForOrphans();

            } catch (Exception e) {
                log.error("Error occurred during bidding scan process: {}", e.getMessage());
                e.printStackTrace();
            }
        }, 0, 10, TimeUnit.SECONDS); // Scans every 10 seconds
    }

    /**
     * Iterates through the in-memory auction list, finalizing those whose time has expired.
     * This method follows the "Release Lock During I/O" pattern to prevent performance bottlenecks.
     */
    private void processRamAuctions() {
        // AuctionManager.getAuctionList() returns a CopyOnWriteArrayList, safe for concurrent iteration.
        for (Auction auction : AuctionManager.getAuctionList()) {
            String auctionId = auction.getId();
            String targetStatus = null;

            // --- PHASE 1: RAM State Check (LOCKED) ---
            // Briefly hold the lock to determine if a status transition is needed.
            synchronized (AuctionManager.getLockForAuction(auctionId)) {
                String currentStatus = auction.getStatus();
                LocalDateTime now = LocalDateTime.now();

                // Check for auction start
                if (currentStatus.equals(Auction.STATUS_OPEN) && now.isAfter(auction.getStartTime())) {
                    if (now.isBefore(auction.getEndTime())) {
                        targetStatus = Auction.STATUS_RUNNING;
                    }
                }
                // Check for auction end
                else if (currentStatus.equals(Auction.STATUS_RUNNING) || currentStatus.equals(Auction.STATUS_OPEN)) {
                    if (now.isAfter(auction.getEndTime())) {
                        targetStatus = (auction.getWinningBidder() != null) ? Auction.STATUS_PAID : Auction.STATUS_CANCELED;
                    }
                }
            }

            // --- PHASE 2: Database I/O (UNLOCKED) ---
            // Execute the blocking database update without holding any RAM locks.
            if (targetStatus != null) {
                try {
                    // Critical RAM-DB consistency check: only update RAM if DB update is successful
                    boolean dbSuccess = auctionDAO.updateAuctionStatus(auctionId, targetStatus);

                    if (dbSuccess) {
                        // --- PHASE 3: RAM State Update (LOCKED) ---
                        // Re-acquire the lock to apply the committed DB state back to RAM.
                        synchronized (AuctionManager.getLockForAuction(auctionId)) {
                            auction.setStatus(targetStatus);

                            if (targetStatus.equals(Auction.STATUS_PAID)) {
                                processFinancialSettlement(auction);
                                log.info("Auction {} finished | Winner: {}", auctionId, (auction.getWinningBidder() != null ? auction.getWinningBidder().getUserName() : "N/A"));
                            } else if (targetStatus.equals(Auction.STATUS_RUNNING)) {
                                log.info("Auction {} is running.", auctionId);
                            } else {
                                log.info("Auction {} finished.", auctionId);
                            }
                        }
                    } else {
                        // DB update failed, do NOT update RAM.
                        log.warn("Failed to update database for auction {}. Skipping RAM update.", auctionId);
                    }
                } catch (Exception e) {
                    log.error("Failed to update auction {} to {}: {}", auctionId, targetStatus, e.getMessage());
                }
            }

            // --- PHASE 4: RAM Cleanup for Terminal States (LOCKED) ---
            // Terminal states require removal from the active monitoring list.
            synchronized (AuctionManager.getLockForAuction(auctionId)) {
                String finalStatus = auction.getStatus();
                if (finalStatus.equals(Auction.STATUS_PAID) ||
                        finalStatus.equals(Auction.STATUS_CANCELED) ||
                        finalStatus.equals(Auction.STATUS_DELETED)) {

                    // Remove from Server RAM to prevent memory leaks
                    allAuctions.remove(auction);
                    AuctionManager.removeAuctionLock(auctionId);

                    // Broadcast removal command to all connected clients
                    ClientManager.broadcast("REMOVE_AUCTION", auctionId, null);

                    log.info("Removed auction {} from RAM.", auctionId);
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
                    log.info("Financial settlement completed for auction {}", auction.getId());
                    return true;

                } catch (Exception e) {
                    conn.rollback(); // Rollback if any error occurs
                    log.error("Error during financial settlement: {}", e.getMessage());
                    return false;
                }
            } catch (Exception e) {
                log.error("Connection Error during settlement: {}", e.getMessage());
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
                log.error("Orphan sweep error: {}", e.getMessage());
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
        log.info("Auction Monitor shutdown.");
    }
}
