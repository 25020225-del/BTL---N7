package database.dao;

import database.DatabaseManager;
import model.auction.Auction;
import model.item.Item;
import model.user.User;
import service.AutoBidLockService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for all bid-related persistence operations.
 *
 * <h2>Financial Safety Guarantee</h2>
 * <p>Every method that writes wallet state runs inside a single JDBC transaction
 * ({@code autoCommit=false}) to ensure <em>atomicity</em>: either the bid row,
 * the wallet lock, and the auction update all commit together, or none of them do.</p>
 *
 * <h2>Key Change: saveAutoBid now delegates wallet ops to AutoBidLockService</h2>
 * <p>The previous implementation had the lock logic inlined. It is now extracted to
 * {@link AutoBidLockService} so the same logic can be tested in isolation and reused
 * during auction settlement (win deduction, expiry unlock).</p>
 */
public class BidDAO {

    private final WalletDAO walletDAO = new WalletDAO();

    /**
     * Injected lock service — defaults to a WalletDAO-backed instance.
     * Can be replaced in tests via {@link #setBidLockService(AutoBidLockService)}.
     */
    private AutoBidLockService autoBidLockService = new AutoBidLockService(walletDAO);

    /** Allows injection of a test double for the lock service. */
    public void setBidLockService(AutoBidLockService service) {
        this.autoBidLockService = service;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner types
    // ─────────────────────────────────────────────────────────────────────────

    /** Immutable snapshot of the auction state after a successful bid commit. */
    public static final class BidCommitResult {
        public final String        auctionId;
        public final double        newCurrentPrice;
        public final double        newHighestMaxBid;
        public final String        newWinnerId;
        public final LocalDateTime newEndTime;

        public BidCommitResult(String auctionId, long newCurrentPrice,
                               long newHighestMaxBid, String newWinnerId,
                               LocalDateTime newEndTime) {
            this.auctionId        = auctionId;
            this.newCurrentPrice  = newCurrentPrice;
            this.newHighestMaxBid = newHighestMaxBid;
            this.newWinnerId      = newWinnerId;
            this.newEndTime       = newEndTime;
        }
    }

    /** Thrown when a user's available balance is too low to place a bid. */
    public static class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(String message) {
            super(message);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core bid transaction
    // ─────────────────────────────────────────────────────────────────────────

    private boolean wasPreviousBidBot(Connection conn, String auctionId, String previousWinnerId) {
        if (previousWinnerId == null) return false;
        String sql = "SELECT is_bot FROM bid_transactions "
                + "WHERE auction_id = ? AND bidder_id = ? ORDER BY bid_time DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            ps.setString(2, previousWinnerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("is_bot") == 1;
            }
        } catch (SQLException e) {
            // Safe fallback: assume it was NOT a bot so the unlock path is taken conservatively
        }
        return false;
    }

    /**
     * The single source of truth for committing a bid into the database.
     * Uses optimistic locking (CAS on {@code current_price} + {@code highest_max_bid} +
     * {@code winning_bidder_id}) so concurrent requests self-resolve without external locks.
     *
     * <p><b>Wallet handling:</b></p>
     * <ul>
     *   <li>Manual bid ({@code isBot=false}): calls {@link WalletDAO#lockBalance} to reserve funds.</li>
     *   <li>Bot bid ({@code isBot=true}): funds were <em>already locked</em> by
     *       {@link #saveAutoBid}; this method only unlocks the <em>previous winner's</em>
     *       reservation when ownership changes. It does NOT re-lock the bot's funds.</li>
     * </ul>
     *
     * @return a {@link BidCommitResult} on success, or {@code null} on optimistic conflict.
     * @throws InsufficientFundsException if the user's available balance cannot cover the bid.
     * @throws SQLException               on any other DB failure.
     */
    public BidCommitResult executeBidTransactionSourceOfTruth(
            Connection conn,
            String     auctionId,
            User       currentUser,
            long       newMaxBid,
            long       expectedPrice,
            long       expectedMaxBid,
            String     expectedWinnerId,
            boolean    isBot
    ) throws SQLException {

        // ── Step 0: Read current auction state (FOR UPDATE-style via optimistic check) ──
        final String selectSql =
                "SELECT starting_price, current_price, highest_max_bid, bid_increment, "
                        + "start_time, end_time, status, winning_bidder_id, seller_id "
                        + "FROM auctions WHERE id = ?";

        long          startingPrice, currentPrice, highestMaxBid, bidIncrement;
        LocalDateTime startTime, endTime;
        String        status, winningBidderId, sellerId;

        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                status = rs.getString("status");
                // Layer-2 defence: abort if DB says auction is no longer RUNNING
                if (!Auction.STATUS_RUNNING.equals(status)) return null;

                startingPrice   = rs.getLong("starting_price");
                currentPrice    = rs.getLong("current_price");
                highestMaxBid   = rs.getLong("highest_max_bid");
                bidIncrement    = rs.getLong("bid_increment");
                startTime       = LocalDateTime.parse(rs.getString("start_time"));
                endTime         = LocalDateTime.parse(rs.getString("end_time"));
                winningBidderId = rs.getString("winning_bidder_id");
                sellerId        = rs.getString("seller_id");
            }
        }

        // ── Step 1: Build in-memory auction snapshot to calculate bid outcome ──
        Auction auctionSnapshot = new Auction();
        auctionSnapshot.setId(auctionId);

        Item item = new Item();
        item.setStartingPrice(startingPrice);
        auctionSnapshot.setItem(item);

        User seller = new User();
        seller.setId(sellerId);
        auctionSnapshot.setSeller(seller);

        auctionSnapshot.setCurrentPrice(currentPrice);
        auctionSnapshot.setHighestMaxBid(highestMaxBid);
        auctionSnapshot.setBidIncrement(bidIncrement);
        auctionSnapshot.setStartTime(startTime);
        auctionSnapshot.setEndTime(endTime);
        auctionSnapshot.setStatus(status);
        auctionSnapshot.setMaxEndTime(endTime.plusMinutes(30));

        if (winningBidderId != null) {
            User winner = new User();
            winner.setId(winningBidderId);
            auctionSnapshot.setWinningBidder(winner);
        }

        Auction.BidResult result = auctionSnapshot.calculateBidResult(currentUser, newMaxBid);
        if (result == null) return null;

        // ── Step 2: Wallet adjustments ──────────────────────────────────────────
        String now                  = LocalDateTime.now().toString();
        long   previousHighestMaxBid = highestMaxBid;
        boolean wasPreviousWinnerBot = wasPreviousBidBot(conn, auctionId, winningBidderId);

        if (result.newWinner != null && result.newWinner.getId().equals(currentUser.getId())) {

            if (winningBidderId != null && winningBidderId.equals(currentUser.getId())) {
                // ── Scenario A: Same user is raising their own max bid ──────────
                long extraAmount = newMaxBid - previousHighestMaxBid;
                if (extraAmount > 0 && !isBot) {
                    // Manual user raising their bid: lock the additional increment
                    if (!walletDAO.lockBalance(conn, currentUser.getId(), extraAmount)) {
                        throw new InsufficientFundsException("Số dư không đủ để nâng mức đặt giá.");
                    }
                    walletDAO.addTransaction(conn,
                            "W-LCK-" + java.util.UUID.randomUUID(),
                            currentUser.getId(),
                            -extraAmount,
                            "Lock incremental bid raise for auction: " + auctionId,
                            now);
                }
                // For isBot=true: the extra amount was already covered when the user
                // registered / upgraded their auto-bid (handled by AutoBidLockService).

            } else {
                // ── Scenario B: This user takes the lead from another user ────────

                // 2a. Unlock the funds of the PREVIOUS winner
                if (winningBidderId != null && !wasPreviousWinnerBot) {
                    // Previous winner was a manual bidder: release their locked funds
                    User previousWinner = new User();
                    previousWinner.setId(winningBidderId);
                    walletDAO.unlockBalance(conn, previousWinner.getId(), previousHighestMaxBid);
                    walletDAO.addTransaction(conn,
                            "W-UNL-" + java.util.UUID.randomUUID(),
                            previousWinner.getId(),
                            previousHighestMaxBid,
                            "Unlock outbid reserve for auction: " + auctionId,
                            now);
                }
                // If the previous winner WAS a bot, their funds were locked at auto-bid
                // registration time and must NOT be unlocked here — the AutoBidEngine
                // will remove that bot from the queue when it loses, and AuctionMonitor
                // will release the reservation at auction close.

                // 2b. Lock the NEW winner's funds (manual path only)
                if (!isBot) {
                    if (!walletDAO.lockBalance(conn, currentUser.getId(), newMaxBid)) {
                        throw new InsufficientFundsException("Số dư không đủ để đặt giá.");
                    }
                    walletDAO.addTransaction(conn,
                            "W-LCK-" + java.util.UUID.randomUUID(),
                            currentUser.getId(),
                            -newMaxBid,
                            "Lock bid reserve for auction: " + auctionId,
                            now);
                }
                // For isBot=true: locked at auto-bid registration — nothing to do here.
            }
        }

        // ── Step 3: Append bid history row ─────────────────────────────────────
        final String insertBidSql =
                "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, bid_time, is_bot) "
                        + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertBidSql)) {
            ps.setString(1, "BID-" + java.util.UUID.randomUUID());
            ps.setString(2, auctionId);
            ps.setString(3, currentUser.getId());
            ps.setLong(4, result.newCurrentPrice);
            ps.setString(5, now);
            ps.setInt(6, isBot ? 1 : 0);
            ps.executeUpdate();
        }

        // ── Step 4: Update auction row — CAS on price + winner + status ─────────
        // Layer-3 defence: reject the write if another thread already changed the DB.
        final String updateSql;
        if (winningBidderId == null) {
            updateSql = "UPDATE auctions "
                    + "SET current_price = ?, end_time = ?, winning_bidder_id = ?, highest_max_bid = ? "
                    + "WHERE id = ? AND current_price = ? AND highest_max_bid = ? "
                    + "AND winning_bidder_id IS NULL AND status = 'RUNNING'";
        } else {
            updateSql = "UPDATE auctions "
                    + "SET current_price = ?, end_time = ?, winning_bidder_id = ?, highest_max_bid = ? "
                    + "WHERE id = ? AND current_price = ? AND highest_max_bid = ? "
                    + "AND winning_bidder_id = ? AND status = 'RUNNING'";
        }

        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setLong(1,   result.newCurrentPrice);
            ps.setString(2, result.newEndTime.toString());
            ps.setString(3, result.newWinner != null ? result.newWinner.getId() : null);
            ps.setLong(4,   result.newHighestMaxBid);
            ps.setString(5, auctionId);
            ps.setLong(6,   currentPrice);     // CAS: expected current price
            ps.setLong(7,   highestMaxBid);    // CAS: expected max bid

            if (winningBidderId != null) {
                ps.setString(8, winningBidderId); // CAS: expected winner
            }

            // 0 rows updated → another bid was committed in between → optimistic conflict
            if (ps.executeUpdate() == 0) return null;
        }

        return new BidCommitResult(
                auctionId,
                result.newCurrentPrice,
                result.newHighestMaxBid,
                result.newWinner != null ? result.newWinner.getId() : null,
                result.newEndTime
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Auto-Bid registration
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Persists an Auto-Bid configuration and atomically adjusts the wallet lock,
     * all within a single DB transaction.
     *
     * <h3>Financial invariant</h3>
     * <p>The user's {@code locked_balance} is always kept in sync with their registered
     * {@code max_bid}. If the user upgrades their maxBid, the delta is locked immediately.
     * If they downgrade, the surplus is released. This prevents "phantom bidding" where a
     * bot can fire without the user having sufficient funds to back the bid.</p>
     *
     * @param currentUser The bidder registering the auto-bid.
     * @param auction     The target auction (must be RUNNING).
     * @param maxBid      The maximum amount the bot will not exceed.
     * @param increment   The step increment for each automatic outbid.
     * @return {@code true} on success; {@code false} if the balance is insufficient or
     *         a DB error occurred.
     * @throws SQLException if a non-recoverable DB error occurs outside the lock check.
     */
    public boolean saveAutoBid(User currentUser, Auction auction, long maxBid, long increment)
            throws SQLException {

        final String checkSql  = "SELECT max_bid FROM auto_bids WHERE auction_id = ? AND bidder_id = ?";
        final String upsertSql =
                "INSERT OR REPLACE INTO auto_bids "
                        + "(id, auction_id, bidder_id, max_bid, increment_amount, is_active) "
                        + "VALUES (?, ?, ?, ?, ?, 1)";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // ── 1. Determine existing reservation (if any) ────────────────
                long oldMaxBid = 0;
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setString(1, auction.getId());
                    ps.setString(2, currentUser.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) oldMaxBid = rs.getLong("max_bid");
                    }
                }

                // ── 2. Adjust wallet lock atomically ─────────────────────────
                // Delegate to AutoBidLockService so the logic lives in one place
                // and the connection is shared with the upsert below.
                autoBidLockService.applyLockDifference(conn, currentUser, oldMaxBid, maxBid, auction.getId());

                // ── 3. Persist the auto-bid row ───────────────────────────────
                try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                    // Use a UUID so INSERT OR REPLACE doesn't break FK constraints
                    // when the PK changes (pure convenience; the UNIQUE index on
                    // (auction_id, bidder_id) drives the REPLACE semantics).
                    ps.setString(1, "AB-" + java.util.UUID.randomUUID());
                    ps.setString(2, auction.getId());
                    ps.setString(3, currentUser.getId());
                    ps.setLong(4,   maxBid);
                    ps.setLong(5,   increment);
                    ps.executeUpdate();
                }

                conn.commit();
                return true;

            } catch (AutoBidLockService.InsufficientFundsException e) {
                // Not enough available balance — roll back the entire unit
                conn.rollback();
                return false;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Cancels an active Auto-Bid and releases the associated wallet lock atomically.
     *
     * @param currentUser The bidder cancelling their auto-bid.
     * @param auction     The target auction.
     * @return {@code true} if the cancellation was committed; {@code false} if no
     *         active auto-bid exists or a DB error occurred.
     * @throws SQLException on non-recoverable DB failure.
     */
    public boolean cancelAutoBid(User currentUser, Auction auction) throws SQLException {
        final String checkSql  = "SELECT max_bid FROM auto_bids WHERE auction_id = ? AND bidder_id = ? AND is_active = 1";
        final String deleteSql = "UPDATE auto_bids SET is_active = 0 WHERE auction_id = ? AND bidder_id = ?";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long currentMaxBid = 0;
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setString(1, auction.getId());
                    ps.setString(2, currentUser.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false; // Nothing to cancel
                        }
                        currentMaxBid = rs.getLong("max_bid");
                    }
                }

                // Release all locked funds for this auto-bid
                autoBidLockService.releaseAllLocks(conn, currentUser, currentMaxBid, auction.getId());

                // Deactivate (soft-delete) the auto-bid row
                try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    ps.setString(1, auction.getId());
                    ps.setString(2, currentUser.getId());
                    ps.executeUpdate();
                }

                conn.commit();
                return true;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Query helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves all bid transactions for the given auction, ordered chronologically.
     *
     * @param auctionId The ID of the auction to query.
     * @return An ordered list of maps containing {@code bid_amount} and {@code bid_time}.
     * @throws SQLException if the query fails.
     */
    public List<Map<String, Object>> getTransactionsForAuction(String auctionId) throws SQLException {
        final String sql =
                "SELECT bid_amount, bid_time FROM bid_transactions "
                        + "WHERE auction_id = ? ORDER BY bid_time ASC";
        List<Map<String, Object>> list = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("bid_amount", rs.getLong("bid_amount"));
                    row.put("bid_time",   rs.getString("bid_time"));
                    list.add(row);
                }
            }
        }
        return list;
    }
}