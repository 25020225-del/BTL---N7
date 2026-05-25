package service;

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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Service orchestrating authoritative administrative lifecycle operations for active auctions.
 * Guarantees cross-user financial escrow holds liquidation within a single atomic database transaction context.
 */
public class AdminAuctionService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuctionService.class);
    private final AuctionDAO auctionDAO;
    private final WalletDAO walletDAO;

    public AdminAuctionService(AuctionDAO auctionDAO, WalletDAO walletDAO) {
        this.auctionDAO = auctionDAO;
        this.walletDAO = walletDAO;
    }

    public enum CancelResult {
        SUCCESS,
        NOT_FOUND,
        NOT_CANCELLABLE,
        DB_ERROR
    }

    /**
     * Forcibly liquidates an active auction instance, voids security states, and restores
     * exact financial collateral guarantees back to manual and proxy participant ledgers.
     *
     * @param auctionId unique system identifier matching the target resource aggregate
     * @return a concrete {@link CancelResult} documenting the explicit completion boundary status
     */
    public CancelResult cancelAuctionAndRefund(String auctionId) {
        log.info("[CANCEL] Admin initiated cancel for auction: {}", auctionId);

        Auction ramAuction = AuctionManager.getAuctionList().stream()
                .filter(a -> auctionId.equals(a.getId()))
                .findFirst()
                .orElse(null);

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try {
                AuctionSnapshot snapshot = readAuctionSnapshot(conn, auctionId);
                if (snapshot == null) {
                    conn.rollback();
                    log.warn("[CANCEL] Auction not found in DB: {}", auctionId);
                    return CancelResult.NOT_FOUND;
                }

                int updatedRows = markAuctionAsCancelled(conn, auctionId);
                if (updatedRows == 0) {
                    conn.rollback();
                    log.warn("[CANCEL] Auction {} is not in a cancellable state (status={})",
                            auctionId, snapshot.currentStatus);
                    return CancelResult.NOT_CANCELLABLE;
                }
                log.info("[CANCEL] Auction {} status → CANCELLED (was: {})", auctionId, snapshot.currentStatus);

                if (snapshot.winnerUserId != null && snapshot.highestMaxBid > 0) {
                    long winnerBotMaxBid = fetchWinnerAutoBidMaxBid(conn, auctionId, snapshot.winnerUserId);
                    long totalWinnerRefund = Math.max(snapshot.highestMaxBid, winnerBotMaxBid);

                    refundManualWinner(conn, auctionId, snapshot.winnerUserId, totalWinnerRefund);
                    log.info("[CANCEL] Winner {} fully refunded {} VND (highestMaxBid={}, botMaxBid={}) for auction {}",
                            snapshot.winnerUserId, totalWinnerRefund, snapshot.highestMaxBid, winnerBotMaxBid, auctionId);
                } else {
                    log.info("[CANCEL] Auction {} had no winning bidder — skipping manual refund.", auctionId);
                }

                List<AutoBidRecord> activeBids = fetchAllActiveAutoBids(conn, auctionId);
                log.info("[CANCEL] Found {} active AutoBid user(s) to refund for auction {}",
                        activeBids.size(), auctionId);

                for (AutoBidRecord record : activeBids) {
                    if (snapshot.winnerUserId != null && record.userId.equals(snapshot.winnerUserId)) {
                        log.info("[CANCEL] Skipping double refund for winning active AutoBid user {}", record.userId);
                        continue;
                    }
                    if (record.maxBid <= 0) {
                        log.warn("[CANCEL] Skipping zero-amount AutoBid for user {} on auction {}",
                                record.userId, auctionId);
                        continue;
                    }
                    refundAutoBidUser(conn, auctionId, record.userId, record.maxBid);
                    log.info("[CANCEL] AutoBid user {} refunded {} VND for auction {}",
                            record.userId, record.maxBid, auctionId);
                }

                deactivateAllAutoBids(conn, auctionId);
                conn.commit();

                log.info("[CANCEL] ✅ Auction {} fully cancelled and all {} user(s) refunded. COMMIT OK.",
                        auctionId, activeBids.size());
                syncRamAfterSuccessfulCancel(auctionId, ramAuction);

                return CancelResult.SUCCESS;

            } catch (SQLException e) {
                safeRollback(conn, auctionId, e);
                return CancelResult.DB_ERROR;
            }

        } catch (SQLException e) {
            log.error("[CANCEL] Failed to obtain DB connection for auction {}: {}", auctionId, e.getMessage(), e);
            return CancelResult.DB_ERROR;
        }
    }

    public CompletableFuture<CancelResult> cancelAuctionAndRefundAsync(String auctionId) {
        Callable<CancelResult> task = () -> cancelAuctionAndRefund(auctionId);
        return TransactionManager.submitTask(task);
    }

    private AuctionSnapshot readAuctionSnapshot(Connection conn, String auctionId) throws SQLException {
        final String sql = "SELECT status, winning_bidder_id, highest_max_bid FROM auctions WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                AuctionSnapshot snap = new AuctionSnapshot();
                snap.currentStatus  = rs.getString("status");
                snap.winnerUserId   = rs.getString("winning_bidder_id");
                snap.highestMaxBid  = rs.getLong("highest_max_bid");
                return snap;
            }
        }
    }

    private int markAuctionAsCancelled(Connection conn, String auctionId) throws SQLException {
        final String sql = "UPDATE auctions SET status = ? WHERE id = ? AND status IN (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Auction.STATUS_CANCELED);
            ps.setString(2, auctionId);
            ps.setString(3, Auction.STATUS_OPEN);
            ps.setString(4, Auction.STATUS_WAITING_FOR_BID);
            ps.setString(5, Auction.STATUS_RUNNING);
            return ps.executeUpdate();
        }
    }

    private long fetchWinnerAutoBidMaxBid(Connection conn, String auctionId, String winnerUserId) throws SQLException {
        final String sql = "SELECT max_bid FROM auto_bids WHERE auction_id = ? AND bidder_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            ps.setString(2, winnerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("max_bid") : 0L;
            }
        }
    }

    private void refundManualWinner(Connection conn, String auctionId, String winnerUserId, long amount) throws SQLException {
        String now = LocalDateTime.now().toString();
        walletDAO.unlockBalance(conn, winnerUserId, amount);
        walletDAO.addTransaction(conn, "CANCEL-REFUND-WIN-" + UUID.randomUUID(),
                winnerUserId, amount,
                "Refund: auction CANCELLED by Admin — manual bid reserve released for auction: " + auctionId, now);
    }

    private List<AutoBidRecord> fetchAllActiveAutoBids(Connection conn, String auctionId) throws SQLException {
        final String sql = "SELECT bidder_id, max_bid FROM auto_bids WHERE auction_id = ? AND is_active = 1";
        List<AutoBidRecord> records = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AutoBidRecord rec = new AutoBidRecord();
                    rec.userId = rs.getString("bidder_id");
                    rec.maxBid = rs.getLong("max_bid");
                    records.add(rec);
                }
            }
        }
        return records;
    }

    private void refundAutoBidUser(Connection conn, String auctionId, String userId, long maxBid) throws SQLException {
        String now = LocalDateTime.now().toString();
        walletDAO.unlockBalance(conn, userId, maxBid);
        walletDAO.addTransaction(conn, "CANCEL-REFUND-AB-" + UUID.randomUUID(),
                userId, maxBid,
                "Refund: auction CANCELLED by Admin — auto-bid reserve released for auction: " + auctionId, now);
    }

    private void deactivateAllAutoBids(Connection conn, String auctionId) throws SQLException {
        final String sql = "UPDATE auto_bids SET is_active = 0 WHERE auction_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            ps.executeUpdate();
        }
    }

    private void syncRamAfterSuccessfulCancel(String auctionId, Auction ramAuction) {
        if (ramAuction != null) {
            synchronized (AuctionManager.getLockForAuction(auctionId)) {
                ramAuction.setStatus(Auction.STATUS_CANCELED);
                ramAuction.getActiveAutoBids().clear();
            }
            AuctionManager.getAuctionList().remove(ramAuction);
            AuctionManager.removeAuctionLock(auctionId);
            log.info("[CANCEL] RAM state cleaned for auction {}", auctionId);
        }

        ClientManager.broadcast("AUCTION_CANCELLED", Map.of(
                "auctionId", auctionId,
                "message",   "Phiên đấu giá đã bị Admin hủy. Tiền đặt giá sẽ được hoàn lại."
        ), null);
    }

    private void safeRollback(Connection conn, String auctionId, SQLException originalError) {
        log.error("[CANCEL] ❌ SQL error during cancel for auction {}. Initiating ROLLBACK. Cause: {}",
                auctionId, originalError.getMessage(), originalError);
        try {
            conn.rollback();
            log.warn("[CANCEL] ROLLBACK successful for auction {}. No financial data was mutated.", auctionId);
        } catch (SQLException rollbackEx) {
            log.error("[CANCEL] ☠ CRITICAL: ROLLBACK FAILED for auction {}! Manual DB inspection required! "
                    + "RollbackError: {}", auctionId, rollbackEx.getMessage(), rollbackEx);
        }
    }

    private static final class AuctionSnapshot {
        String currentStatus;
        String winnerUserId;
        long   highestMaxBid;
    }

    private static final class AutoBidRecord {
        String userId;
        long   maxBid;
    }
}