package service;

import database.dao.WalletDAO;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service managing atomic available wallet asset holds backing proxy bot registrations.
 * Adheres strictly to an external connection state model where the parent caller maintains transaction scope boundaries.
 */
public final class AutoBidLockService {

    private static final Logger log = LoggerFactory.getLogger(AutoBidLockService.class);
    private final WalletDAO walletDAO;

    public AutoBidLockService(WalletDAO walletDAO) {
        this.walletDAO = walletDAO;
    }

    /**
     * Mutates the available asset holding metrics by calculating the difference between historical and active cap ceilings.
     *
     * @param conn      shared transaction database entry point connection resource
     * @param user      targeted entity requiring profile balance hold modifications
     * @param oldMaxBid historical max bid record ceiling mapping
     * @param newMaxBid top currency threshold bound evaluated for hold capping
     * @param auctionId logging and transaction history routing contextual key
     * @throws InsufficientFundsException if the active asset wallet cannot safely buffer the pledge bounds
     * @throws SQLException               on underlying data manipulation error states
     */
    public void applyLockDifference(Connection conn, User user, long oldMaxBid, long newMaxBid, String auctionId)
            throws InsufficientFundsException, SQLException {

        long delta = newMaxBid - oldMaxBid;
        if (delta == 0) return;

        String now = LocalDateTime.now().toString();

        if (delta > 0) {
            boolean locked = walletDAO.lockBalance(conn, user.getId(), delta);
            if (!locked) {
                log.warn("Auto-bid lock FAILED (insufficient funds): userId={}, auctionId={}, Δ={}", user.getId(), auctionId, delta);
                throw new InsufficientFundsException("Số dư khả dụng không đủ để đặt cược Auto Bid tối đa " + newMaxBid + " VNĐ. Cần thêm " + delta + " VNĐ.");
            }
            walletDAO.addTransaction(conn, "AB-LCK-" + UUID.randomUUID(), user.getId(), -delta,
                    "Lock incremental auto-bid reserve (+" + delta + ") for auction: " + auctionId, now);
            log.info("Auto-bid lock OK: +{} locked. userId={}, auctionId={}", delta, user.getId(), auctionId);
        } else {
            long release = -delta;
            walletDAO.unlockBalance(conn, user.getId(), release);
            walletDAO.addTransaction(conn, "AB-UNL-" + UUID.randomUUID(), user.getId(), release,
                    "Unlock auto-bid reserve downgrade (−" + release + ") for auction: " + auctionId, now);
            log.info("Auto-bid lock reduced: −{} released. userId={}, auctionId={}", release, user.getId(), auctionId);
        }
    }

    public void releaseAllLocks(Connection conn, User user, long currentMaxBid, String auctionId) throws SQLException {
        if (currentMaxBid <= 0) return;

        String now = LocalDateTime.now().toString();
        walletDAO.unlockBalance(conn, user.getId(), currentMaxBid);
        walletDAO.addTransaction(conn, "AB-UNL-" + UUID.randomUUID(), user.getId(), currentMaxBid,
                "Full auto-bid reserve release (cancel/expire) for auction: " + auctionId, now);
        log.info("Full auto-bid reserve released: {} → userId={}, auctionId={}", currentMaxBid, user.getId(), auctionId);
    }

    public boolean finalizeWinDeduction(Connection conn, User user, long winningPrice, String auctionId) throws SQLException {
        String now = LocalDateTime.now().toString();
        boolean success = walletDAO.deductFromLocked(conn, user.getId(), winningPrice);
        if (success) {
            walletDAO.addTransaction(conn, "AB-WIN-" + UUID.randomUUID(), user.getId(), -winningPrice,
                    "Auto-bid win settlement (" + winningPrice + ") for auction: " + auctionId, now);
            log.info("Auto-bid win deduction OK: {} charged. userId={}, auctionId={}", winningPrice, user.getId(), auctionId);
        } else {
            log.error("CRITICAL: Auto-bid win deduction FAILED — locked balance mismatch. userId={}, auctionId={}, winningPrice={}", user.getId(), auctionId, winningPrice);
        }
        return success;
    }

    public static final class InsufficientFundsException extends Exception {
        public InsufficientFundsException(String message) {
            super(message);
        }
    }
}