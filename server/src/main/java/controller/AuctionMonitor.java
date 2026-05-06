package controller;

import database.DatabaseManager;
import database.TransactionManager;
import database.dao.AuctionDAO;
import database.dao.WalletDAO;
import model.auction.Auction;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        log.info("Auction monitor has been launched.");

        scheduler.scheduleAtFixedRate(() -> {
            try {
                processRamAuctions();
                sweepDatabaseForOrphans();

            } catch (Exception e) {
                log.error("Error occurred during auction scan process", e);
            }
        }, 0, 10, TimeUnit.SECONDS); // Scans every 10 seconds
    }

    /**
     * Iterates through the in-memory auction list, finalizing those whose time has expired.
     * Uses a guarded Phase 2 check so anti-sniping (end time extension while unlocked) cannot
     * cause a wrongful close; DB updates use optimistic conditions where applicable.
     */
    private void processRamAuctions() {
        for (Auction auction : AuctionManager.getAuctionList()) {
            String auctionId = auction.getId();
            String targetStatus = null;
            LocalDateTime snapshotEndAtDecision = null;

            // PHASE 1: Decide transition under lock + capture end snapshot for expiry paths
            synchronized (AuctionManager.getLockForAuction(auctionId)) {
                String currentStatus = auction.getStatus();
                LocalDateTime now = LocalDateTime.now();

                if (currentStatus.equals(Auction.STATUS_OPEN) && now.isAfter(auction.getStartTime())) {
                    if (now.isBefore(auction.getEndTime())) {
                        targetStatus = Auction.STATUS_RUNNING;
                    }
                } else if (currentStatus.equals(Auction.STATUS_RUNNING)
                        || currentStatus.equals(Auction.STATUS_OPEN)) {
                    if (now.isAfter(auction.getEndTime())) {
                        targetStatus = (auction.getWinningBidder() != null)
                                ? Auction.STATUS_PAID
                                : Auction.STATUS_CANCELED;
                        snapshotEndAtDecision = auction.getEndTime();
                    }
                }
            }

            if (targetStatus == null) {
                finalizeRamCleanupIfTerminal(auction, auctionId);
                continue;
            }

            boolean dbSuccess;

            try {
                if (Auction.STATUS_RUNNING.equals(targetStatus)) {
                    synchronized (AuctionManager.getLockForAuction(auctionId)) {
                        if (!auction.getStatus().equals(Auction.STATUS_OPEN)) {
                            finalizeRamCleanupIfTerminal(auction, auctionId);
                            continue;
                        }
                        LocalDateTime now = LocalDateTime.now();
                        if (!now.isAfter(auction.getStartTime()) || !now.isBefore(auction.getEndTime())) {
                            finalizeRamCleanupIfTerminal(auction, auctionId);
                            continue;
                        }
                    }
                    dbSuccess = auctionDAO.updateAuctionStatusOpenToRunning(auctionId);
                } else {
                    synchronized (AuctionManager.getLockForAuction(auctionId)) {
                        LocalDateTime now = LocalDateTime.now();
                        if (snapshotEndAtDecision == null) {
                            finalizeRamCleanupIfTerminal(auction, auctionId);
                            continue;
                        }
                        if (now.isBefore(auction.getEndTime())) {
                            log.debug("Skipping close for auction {}: still before end time (anti-sniping / clock race)", auctionId);
                            finalizeRamCleanupIfTerminal(auction, auctionId);
                            continue;
                        }
                        if (auction.getEndTime().isAfter(snapshotEndAtDecision)) {
                            log.debug("Skipping close for auction {}: end time was extended since decision snapshot", auctionId);
                            finalizeRamCleanupIfTerminal(auction, auctionId);
                            continue;
                        }
                    }
                    dbSuccess = auctionDAO.updateAuctionStatusEndingIfEndTimeMatches(
                            auctionId,
                            targetStatus,
                            snapshotEndAtDecision);
                }

                if (dbSuccess) {
                    synchronized (AuctionManager.getLockForAuction(auctionId)) {
                        auction.setStatus(targetStatus);

                        if (Auction.STATUS_PAID.equals(targetStatus)) {
                            processFinancialSettlement(auction);
                            log.info("Auction {} finished with winner: {}",
                                    auctionId,
                                    auction.getWinningBidder() != null ? auction.getWinningBidder().getUserName() : "N/A");
                        } else if (Auction.STATUS_RUNNING.equals(targetStatus)) {
                            log.info("Auction {} has started and is now RUNNING.", auctionId);
                        } else {
                            log.info("Auction {} finished with NO winner. CANCELED.", auctionId);
                        }
                    }
                } else {
                    log.warn("Optimistic DB update failed for auction {} to status {} — state may have changed concurrently; RAM not updated.",
                            auctionId, targetStatus);
                }
            } catch (Exception e) {
                log.error("Failed to update auction {} to {}", auctionId, targetStatus, e);
            }

            finalizeRamCleanupIfTerminal(auction, auctionId);
        }
    }

    private void finalizeRamCleanupIfTerminal(Auction auction, String auctionId) {
        synchronized (AuctionManager.getLockForAuction(auctionId)) {
            String finalStatus = auction.getStatus();
            if (finalStatus.equals(Auction.STATUS_PAID)
                    || finalStatus.equals(Auction.STATUS_CANCELED)
                    || finalStatus.equals(Auction.STATUS_DELETED)) {

                allAuctions.remove(auction);
                AuctionManager.removeAuctionLock(auctionId);

                ClientManager.broadcast("REMOVE_AUCTION", auctionId, null);

                log.info("Removed auction {} from RAM. (DB already updated)", auctionId);
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
            return;
        }

        Callable<Boolean> settlementTask = () -> {
            String now = LocalDateTime.now().toString();

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);

                try {
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

                    double refundAmount = auction.getHighestMaxBid() - auction.getCurrentPrice();
                    if (refundAmount > 0) {
                        walletDAO.updateBalance(conn, auction.getWinningBidder().getId(), refundAmount);

                        walletDAO.addTransaction(
                                conn,
                                "W-REF-" + (System.currentTimeMillis() + 1),
                                auction.getWinningBidder().getId(),
                                refundAmount,
                                "Refund for excess max bid on auction: " + auction.getId(),
                                now
                        );
                    }

                    conn.commit();
                    log.info("Financial settlement completed for auction {}", auction.getId());
                    return true;

                } catch (Exception e) {
                    conn.rollback();
                    log.error("Error during financial settlement for auction {}", auction.getId(), e);
                    return false;
                }
            } catch (Exception e) {
                log.error("Connection error during settlement for auction {}", auction.getId(), e);
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
                    processFinancialSettlement(auction);
                    ClientManager.broadcast("REMOVE_AUCTION", auction.getId(), null);
                }
                return true;
            } catch (Exception e) {
                log.error("Orphan sweep error", e);
                return false;
            }
        };

        TransactionManager.submitTask(dbSweepTask);
    }

    /**
     * Gracefully shuts down the monitoring daemon service.
     */
    public void stopMonitoring() {
        scheduler.shutdown();
        log.info("Auction monitor has been shutdown.");
    }
}
