package controller;

import database.DatabaseManager;
import database.TransactionManager;
import database.dao.AuctionDAO;
import database.dao.WalletDAO;
import exception.AuctionExceptions;
import model.auction.Auction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background daemon service monitoring active auctions in real-time.
 * Manages chronological state transitions across volatile regions and executes
 * transactional settlements over completed bidding pools.
 */
public class AuctionMonitor {

    private static final Logger log = LoggerFactory.getLogger(AuctionMonitor.class);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final List<Auction> allAuctions;
    private final AuctionDAO auctionDAO;
    private final WalletDAO walletDAO;

    public AuctionMonitor(List<Auction> allAuctions, AuctionDAO auctionDAO, WalletDAO walletDAO) {
        this.allAuctions = allAuctions;
        this.auctionDAO = auctionDAO;
        this.walletDAO = walletDAO;
    }

    /**
     * Spawns the main runtime pooling block driving cron evaluation over memory tables.
     */
    public void startMonitoring() {
        log.info("Auction monitor daemon initiated.");
        scheduler.scheduleAtFixedRate(() -> {
            try {
                processRamAuctions();
                sweepDatabaseForOrphans();
            } catch (Exception e) {
                log.error("Unhandled exception intercepted inside auction monitoring heartbeat loop", e);
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    /**
     * Shuts down chronological calculation frameworks safely.
     */
    public void stopMonitoring() {
        scheduler.shutdown();
        log.info("Auction monitor daemon terminated successfully.");
    }

    private void processRamAuctions() {
        for (Auction auction : AuctionManager.getAuctionList()) {
            String auctionId = auction.getId();
            String targetStatus = null;
            LocalDateTime snapshotEndAtDecision = null;

            synchronized (AuctionManager.getLockForAuction(auctionId)) {
                String currentStatus = auction.getStatus();
                LocalDateTime now = LocalDateTime.now();

                if (currentStatus.equals(Auction.STATUS_OPEN) && now.isAfter(auction.getStartTime())) {
                    if (now.isBefore(auction.getEndTime())) {
                        targetStatus = Auction.STATUS_RUNNING;
                    }
                } else if (currentStatus.equals(Auction.STATUS_RUNNING) || currentStatus.equals(Auction.STATUS_OPEN)) {
                    if (now.isAfter(auction.getEndTime())) {
                        targetStatus = Auction.STATUS_FINISHED;
                        snapshotEndAtDecision = auction.getEndTime();
                    }
                } else if (currentStatus.equals(Auction.STATUS_WAITING_FOR_BID)) {
                    if (now.isAfter(auction.getEndTime())) {
                        targetStatus = Auction.STATUS_CANCELED;
                        snapshotEndAtDecision = auction.getEndTime();
                    }
                }
            }

            if (targetStatus == null) {
                finalizeRamCleanupIfTerminal(auction, auctionId);
                continue;
            }

            try {
                boolean dbSuccess = Auction.STATUS_RUNNING.equals(targetStatus)
                        ? tryTransitionToRunning(auction, auctionId)
                        : tryTransitionToFinished(auction, auctionId, snapshotEndAtDecision, targetStatus);

                if (dbSuccess) {
                    applyStatusTransitionToRam(auction, auctionId, targetStatus);
                } else {
                    log.warn("Optimistic DB update failed for auction {} -> {} due to concurrent modification.",
                            auctionId, targetStatus);
                }
            } catch (SQLException e) {
                log.error("Database error updating auction {} to status {}", auctionId, targetStatus, e);
            }

            finalizeRamCleanupIfTerminal(auction, auctionId);
        }
    }

    private boolean tryTransitionToRunning(Auction auction, String auctionId) throws SQLException {
        synchronized (AuctionManager.getLockForAuction(auctionId)) {
            if (!auction.getStatus().equals(Auction.STATUS_OPEN)) {
                finalizeRamCleanupIfTerminal(auction, auctionId);
                return false;
            }
            LocalDateTime now = LocalDateTime.now();
            if (!now.isAfter(auction.getStartTime()) || !now.isBefore(auction.getEndTime())) {
                finalizeRamCleanupIfTerminal(auction, auctionId);
                return false;
            }
        }
        return auctionDAO.updateAuctionStatusOpenToRunning(auctionId);
    }

    private boolean tryTransitionToFinished(Auction auction, String auctionId,
                                            LocalDateTime snapshotEndAtDecision,
                                            String targetStatus) throws SQLException {
        synchronized (AuctionManager.getLockForAuction(auctionId)) {
            if (snapshotEndAtDecision == null) {
                finalizeRamCleanupIfTerminal(auction, auctionId);
                return false;
            }
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(auction.getEndTime())) {
                log.debug("Skipping close for {}: anti-sniping execution guard active.", auctionId);
                finalizeRamCleanupIfTerminal(auction, auctionId);
                return false;
            }
            if (auction.getEndTime().isAfter(snapshotEndAtDecision)) {
                log.debug("Skipping close for {}: end time extended during snapshot resolution.", auctionId);
                finalizeRamCleanupIfTerminal(auction, auctionId);
                return false;
            }
        }
        return auctionDAO.updateAuctionStatusEndingIfEndTimeMatches(auctionId, targetStatus, snapshotEndAtDecision);
    }

    private void applyStatusTransitionToRam(Auction auction, String auctionId, String targetStatus) {
        synchronized (AuctionManager.getLockForAuction(auctionId)) {
            auction.setStatus(targetStatus);
            if (Auction.STATUS_FINISHED.equals(targetStatus)) {
                log.info("Auction {} transitioned to terminal state. Dispatching clearing transactions...", auctionId);
                processFinancialSettlement(auction);
            } else {
                log.info("Auction {} status mapped onto state: {}.", auctionId, targetStatus);
                ClientManager.broadcast("AUCTION_STATUS_CHANGED", Map.of("auctionId", auctionId, "newStatus", targetStatus), null);
            }
        }
    }

    private void finalizeRamCleanupIfTerminal(Auction auction, String auctionId) {
        synchronized (AuctionManager.getLockForAuction(auctionId)) {
            String finalStatus = auction.getStatus();
            boolean isTerminal = finalStatus.equals(Auction.STATUS_PAID)
                    || finalStatus.equals(Auction.STATUS_CANCELED)
                    || finalStatus.equals(Auction.STATUS_DELETED);

            if (isTerminal) {
                allAuctions.remove(auction);
                AuctionManager.removeAuctionLock(auctionId);
                ClientManager.broadcast("REMOVE_AUCTION", auctionId, null);
                log.info("Auction {} evacuated from memory caches.", auctionId);
            }
        }
    }

    private void processFinancialSettlement(Auction auction) {
        Callable<Boolean> settlementTask = () -> {
            String auctionId = auction.getId();
            String finalStatus = (auction.getWinningBidder() != null) ? Auction.STATUS_PAID : Auction.STATUS_CANCELED;

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    if (!lockAuctionForSettlement(conn, auctionId, finalStatus)) {
                        conn.rollback();
                        return false;
                    }

                    if (auction.getWinningBidder() != null) {
                        settleWinner(conn, auction, auctionId);
                    }
                    refundLosingAutoBidders(conn, auction, auctionId);
                    deactivateAutoBids(conn, auctionId);

                    conn.commit();

                    synchronized (AuctionManager.getLockForAuction(auctionId)) {
                        auction.setStatus(finalStatus);
                    }
                    ClientManager.broadcast("AUCTION_STATUS_CHANGED", Map.of("auctionId", auctionId, "newStatus", finalStatus), null);
                    log.info("Financial settlement cleared: auction {} converted to {}", auctionId, finalStatus);
                    return true;

                } catch (AuctionExceptions.InsufficientFundsException e) {
                    conn.rollback();
                    log.error("[CRITICAL][C1] Clearing transaction aborted for auction {}: winner {} exhibits liquidity drop under race. Settlement rolled back.",
                            auctionId, auction.getWinningBidder().getId(), e);
                    return false;

                } catch (SQLException e) {
                    conn.rollback();
                    log.error("SQL boundary crash during settlement calculations for auction {}", auctionId, e);
                    return false;
                }
            } catch (SQLException e) {
                log.error("Connection pool dropped handling settlement operations for asset {}", auction.getId(), e);
                return false;
            }
        };

        TransactionManager.submitTask(settlementTask);
    }

    private boolean lockAuctionForSettlement(Connection conn, String auctionId, String finalStatus) throws SQLException {
        String sql = "UPDATE auctions SET status = ? WHERE id = ? AND status = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, finalStatus);
            ps.setString(2, auctionId);
            ps.setString(3, Auction.STATUS_FINISHED);
            if (ps.executeUpdate() == 0) {
                log.warn("Settlement collision: auction {} already cleared on parallel execution frame.", auctionId);
                return false;
            }
        }
        return true;
    }

    private void settleWinner(Connection conn, Auction auction, String auctionId) throws SQLException {
        String now = LocalDateTime.now().toString();
        long finalPrice = auction.getCurrentPrice();
        long lockedAmount = auction.getHighestMaxBid();
        String winnerId = auction.getWinningBidder().getId();

        boolean deducted = walletDAO.deductFromLocked(conn, winnerId, finalPrice);
        if (!deducted) {
            throw new AuctionExceptions.InsufficientFundsException(
                    String.format("[C1] Collateral assertion failure for winner=%s, asset=%s. Balance bounds violated under data race context.", winnerId, auctionId));
        }

        long refundAmount = lockedAmount - finalPrice;
        if (refundAmount > 0) {
            walletDAO.unlockBalance(conn, winnerId, refundAmount);
            walletDAO.addTransaction(conn, "W-REF-" + UUID.randomUUID(), winnerId, refundAmount,
                    "Refund of excess locked amount for auction: " + auctionId, now);
        }

        walletDAO.updateBalance(conn, auction.getSeller().getId(), finalPrice);
        walletDAO.addTransaction(conn, "W-IN-" + UUID.randomUUID(), auction.getSeller().getId(), finalPrice,
                "Payment received for auction: " + auctionId, now);

        walletDAO.addTransaction(conn, "W-PAY-" + UUID.randomUUID(), winnerId, -finalPrice,
                "Payment for winning auction: " + auctionId, now);
    }

    private void refundLosingAutoBidders(Connection conn, Auction auction, String auctionId) throws SQLException {
        String winnerId = (auction.getWinningBidder() != null) ? auction.getWinningBidder().getId() : "NONE";
        String sql = "SELECT bidder_id, max_bid FROM auto_bids WHERE auction_id = ? AND bidder_id != ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            ps.setString(2, winnerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    walletDAO.unlockBalance(conn, rs.getString("bidder_id"), rs.getLong("max_bid"));
                }
            }
        }
    }

    private void deactivateAutoBids(Connection conn, String auctionId) throws SQLException {
        String sql = "UPDATE auto_bids SET is_active = 0 WHERE auction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            ps.executeUpdate();
        }
    }

    private void sweepDatabaseForOrphans() {
        Callable<Boolean> dbSweepTask = () -> {
            try {
                List<Auction> orphanedAuctions = auctionDAO.sweepOrphanAuctions();
                for (Auction auction : orphanedAuctions) {
                    processFinancialSettlement(auction);
                    ClientManager.broadcast("REMOVE_AUCTION", auction.getId(), null);
                }
                return true;
            } catch (SQLException e) {
                log.error("Database error during orphan auction sweep", e);
                return false;
            }
        };
        TransactionManager.submitTask(dbSweepTask);
    }
}