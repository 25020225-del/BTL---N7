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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
    // Thay thế processFinancialSettlement trong AuctionMonitor.java [cite: 58-74]
    private void processFinancialSettlement(Auction auction) {
        Callable<Boolean> settlementTask = () -> {
            String now = LocalDateTime.now().toString();
            String auctionId = auction.getId();

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // 1. Xử lý người thắng cuộc (Winner)
                    if (auction.getWinningBidder() != null) {
                        double finalPrice = auction.getCurrentPrice();
                        double lockedAmount = auction.getHighestMaxBid();
                        String winnerId = auction.getWinningBidder().getId();

                        // Khấu trừ giá cuối cùng từ tiền tạm giữ của winner
                        walletDAO.deductFromLocked(conn, winnerId, finalPrice);
                        // Hoàn lại phần dư (MaxBid - FinalPrice) cho winner
                        double refundAmount = lockedAmount - finalPrice;
                        if (refundAmount > 0) {
                            walletDAO.unlockBalance(conn, winnerId, refundAmount);
                        }

                        // Trả tiền cho người bán (Seller)
                        walletDAO.updateBalance(conn, auction.getSeller().getId(), finalPrice);
                        walletDAO.addTransaction(conn, "W-IN-" + System.currentTimeMillis(),
                                auction.getSeller().getId(), finalPrice,
                                "Payment received for auction: " + auctionId, now);
                    }

                    // 2. Xử lý những người dùng Auto-Bid đã thua (Losers)
                    // Cần Query tất cả các Auto-Bid của phiên này ngoại trừ người thắng
                    String loserSql = "SELECT bidder_id, max_bid FROM auto_bids WHERE auction_id = ? AND bidder_id != ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(loserSql)) {
                        pstmt.setString(1, auctionId);
                        pstmt.setString(2, auction.getWinningBidder() != null ? auction.getWinningBidder().getId() : "NONE");
                        try (ResultSet rs = pstmt.executeQuery()) {
                            while (rs.next()) {
                                String loserId = rs.getString("bidder_id");
                                double loserMaxBid = rs.getDouble("max_bid");
                                // Hoàn trả toàn bộ tiền tạm giữ cho người thua
                                walletDAO.unlockBalance(conn, loserId, loserMaxBid);
                            }
                        }
                    }

                    // 3. Vô hiệu hóa bot sau khi kết thúc
                    try (PreparedStatement pstmt = conn.prepareStatement("UPDATE auto_bids SET is_active = 0 WHERE auction_id = ?")) {
                        pstmt.setString(1, auctionId);
                        pstmt.executeUpdate();
                    }

                    conn.commit();
                    log.info("Financial settlement (Locked Funds) completed for auction {}", auctionId);
                    return true;
                } catch (Exception e) {
                    conn.rollback();
                    log.error("Settlement error for auction {}", auctionId, e);
                    return false;
                }
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
