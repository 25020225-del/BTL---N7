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
 * Service responsible for atomically managing wallet locks that back Auto-Bid registrations.
 *
 * <h2>Design Contract</h2>
 * <p>Every method in this class accepts an <em>externally-owned</em> {@link Connection} with
 * {@code autoCommit = false}. The service performs only DML; the <strong>caller controls
 * commit / rollback</strong>. This makes every lock operation freely composable into the
 * same DB transaction as the {@code auto_bids} upsert — guaranteeing atomicity without
 * distributed coordination.</p>
 *
 * <h2>Lock Lifecycle</h2>
 * <pre>
 *   Register / Upgrade   → applyLockDifference(+Δ)  → balance ↓, locked_balance ↑
 *   Downgrade            → applyLockDifference(−Δ)  → balance ↑, locked_balance ↓
 *   Cancel               → releaseAllLocks()         → balance ↑, locked_balance = 0 for this slot
 *   Auction Win (bot)    → finalizeWinDeduction()    → locked_balance ↓ (permanent deduct)
 *   Auction Win (manual) → handled by BidDAO normally
 *   Auction Expire (no win) → releaseAllLocks()
 * </pre>
 */
public final class AutoBidLockService {

    private static final Logger log = LoggerFactory.getLogger(AutoBidLockService.class);

    /**
     * Dependency-injected WalletDAO; no static calls inside this service.
     */
    private final WalletDAO walletDAO;

    public AutoBidLockService(WalletDAO walletDAO) {
        this.walletDAO = walletDAO;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Adjusts the wallet lock by the <em>net difference</em> between the new and old
     * Auto-Bid maxBid levels in a single, non-blocking DB update.
     *
     * <ul>
     *   <li>{@code newMaxBid > oldMaxBid} → additional funds are locked (may fail with
     *       {@link InsufficientFundsException} if balance is too low).</li>
     *   <li>{@code newMaxBid < oldMaxBid} → surplus funds are released back to balance.</li>
     *   <li>{@code newMaxBid == oldMaxBid} → no-op.</li>
     * </ul>
     *
     * @param conn      Active connection in an open transaction (autoCommit=false).
     * @param user      The bidder whose wallet is being modified.
     * @param oldMaxBid The previously locked maxBid (0 if this is a fresh registration).
     * @param newMaxBid The desired new maxBid to lock.
     * @param auctionId For logging and wallet-transaction descriptions.
     * @throws InsufficientFundsException if locking more funds is required but balance is too low.
     * @throws SQLException               on DB error (caller must rollback).
     */
    public void applyLockDifference(
            Connection conn,
            User user,
            long oldMaxBid,
            long newMaxBid,
            String auctionId) throws InsufficientFundsException, SQLException {

        long delta = newMaxBid - oldMaxBid;

        if (delta == 0) {
            log.debug("applyLockDifference: no change for userId={}, auctionId={}", user.getId(), auctionId);
            return;
        }

        String now = LocalDateTime.now().toString();

        if (delta > 0) {
            // ── Upgrade path: lock the additional delta ──────────────────────
            boolean locked = walletDAO.lockBalance(conn, user.getId(), delta);
            if (!locked) {
                // Optimistic-lock SQL returned 0 rows → not enough available balance
                log.warn("Auto-bid lock FAILED (insufficient funds): userId={}, auctionId={}, Δ={}",
                        user.getId(), auctionId, delta);
                throw new InsufficientFundsException(
                        "Số dư khả dụng không đủ để đặt cược Auto Bid tối đa "
                                + newMaxBid + " VNĐ. Cần thêm " + delta + " VNĐ.");
            }
            walletDAO.addTransaction(conn,
                    "AB-LCK-" + UUID.randomUUID(),
                    user.getId(),
                    -delta,
                    "Lock incremental auto-bid reserve (+" + delta + ") for auction: " + auctionId,
                    now);
            log.info("Auto-bid lock OK: +{} locked. userId={}, auctionId={}", delta, user.getId(), auctionId);

        } else {
            // ── Downgrade path: release the surplus ──────────────────────────
            long release = -delta; // positive
            walletDAO.unlockBalance(conn, user.getId(), release);
            walletDAO.addTransaction(conn,
                    "AB-UNL-" + UUID.randomUUID(),
                    user.getId(),
                    release,
                    "Unlock auto-bid reserve downgrade (−" + release + ") for auction: " + auctionId,
                    now);
            log.info("Auto-bid lock reduced: −{} released. userId={}, auctionId={}", release, user.getId(), auctionId);
        }
    }

    /**
     * Unconditionally releases <em>all</em> locked funds for a cancelled Auto-Bid.
     * Safe to call with {@code currentMaxBid == 0} (becomes a no-op).
     *
     * @param conn          Active connection in an open transaction.
     * @param user          The bidder whose reservation is being freed.
     * @param currentMaxBid The amount currently locked for this user/auction pair.
     * @param auctionId     For logging / audit trail.
     * @throws SQLException on DB error.
     */
    public void releaseAllLocks(
            Connection conn,
            User user,
            long currentMaxBid,
            String auctionId) throws SQLException {

        if (currentMaxBid <= 0) {
            log.debug("releaseAllLocks: nothing to release. userId={}, auctionId={}", user.getId(), auctionId);
            return;
        }

        String now = LocalDateTime.now().toString();
        walletDAO.unlockBalance(conn, user.getId(), currentMaxBid);
        walletDAO.addTransaction(conn,
                "AB-UNL-" + UUID.randomUUID(),
                user.getId(),
                currentMaxBid,
                "Full auto-bid reserve release (cancel/expire) for auction: " + auctionId,
                now);
        log.info("Full auto-bid reserve released: {} → userId={}, auctionId={}", currentMaxBid, user.getId(), auctionId);
    }

    /**
     * Permanently deducts the winning-bid price from the user's <em>locked balance</em>
     * when an Auto-Bid bot wins an auction. Must be called by {@code AuctionMonitor}
     * during the settlement phase, inside the same close-out transaction.
     *
     * <p>Any remainder in {@code locked_balance} beyond {@code winningPrice} should be
     * subsequently released via {@link #releaseAllLocks} with the difference.</p>
     *
     * @param conn         Active connection in an open transaction.
     * @param user         The winning bidder.
     * @param winningPrice The final auction closing price.
     * @param auctionId    For audit trail.
     * @return {@code true} if deduction succeeded; {@code false} if locked balance was
     * somehow insufficient (indicates a system consistency bug — log as CRITICAL).
     * @throws SQLException on DB error.
     */
    public boolean finalizeWinDeduction(
            Connection conn,
            User user,
            long winningPrice,
            String auctionId) throws SQLException {

        String now = LocalDateTime.now().toString();
        boolean success = walletDAO.deductFromLocked(conn, user.getId(), winningPrice);
        if (success) {
            walletDAO.addTransaction(conn,
                    "AB-WIN-" + UUID.randomUUID(),
                    user.getId(),
                    -winningPrice,
                    "Auto-bid win settlement (" + winningPrice + ") for auction: " + auctionId,
                    now);
            log.info("Auto-bid win deduction OK: {} charged. userId={}, auctionId={}", winningPrice, user.getId(), auctionId);
        } else {
            // This should NEVER happen if applyLockDifference was always called correctly.
            log.error("CRITICAL: Auto-bid win deduction FAILED — locked balance mismatch. " +
                    "userId={}, auctionId={}, winningPrice={}", user.getId(), auctionId, winningPrice);
        }
        return success;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nested Exceptions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Thrown when the user's available balance is insufficient to satisfy an
     * Auto-Bid lock request. Callers should catch this and roll back the transaction.
     */
    public static final class InsufficientFundsException extends Exception {
        public InsufficientFundsException(String message) {
            super(message);
        }
    }
}