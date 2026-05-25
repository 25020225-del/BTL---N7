package database.dao;

import database.DatabaseManager;
import model.auction.Auction;
import model.item.Item;
import model.item.ItemFactory;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data Access Object orchestrating the persistence layer for bidding transactions.
 * Enforces atomic state transitions, manages proxy-bidding configurations,
 * and controls ledger-level escrow balance reservations.
 */
public class BidDAO {

    private static final Logger log = LoggerFactory.getLogger(BidDAO.class);
    private final WalletDAO walletDAO = new WalletDAO();
    private AutoBidLockService autoBidLockService = new AutoBidLockService(walletDAO);

    public void setBidLockService(AutoBidLockService service) {
        this.autoBidLockService = service;
    }

    /**
     * Immutable value capsule encapsulating the verified transactional state mutations
     * computed following a successful database bid commit sequence.
     *
     * FIX #7 (MEDIUM): newCurrentPrice and newHighestMaxBid were incorrectly typed as
     * {@code double} even though the constructor accepted {@code long}. This caused
     * precision loss on large monetary values (e.g., 999_999_999 VNĐ silently becomes
     * 1_000_000_000.0 due to IEEE-754 rounding). Fields are now {@code long} throughout.
     */
    public static final class BidCommitResult {
        public final String auctionId;
        public final long newCurrentPrice;      // FIX #7: was double
        public final long newHighestMaxBid;     // FIX #7: was double
        public final String newWinnerId;
        public final LocalDateTime newEndTime;

        public BidCommitResult(String auctionId, long newCurrentPrice, long newHighestMaxBid,
                               String newWinnerId, LocalDateTime newEndTime) {
            this.auctionId = auctionId;
            this.newCurrentPrice = newCurrentPrice;
            this.newHighestMaxBid = newHighestMaxBid;
            this.newWinnerId = newWinnerId;
            this.newEndTime = newEndTime;
        }
    }

    /**
     * Thrown when an actor attempts a state-changing bidding operation without satisfying
     * the minimum requisite financial escrow overhead balance constraints.
     */
    public static class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(String message) {
            super(message);
        }
    }

    private boolean wasPreviousBidBot(Connection conn, String auctionId, String previousWinnerId) {
        if (previousWinnerId == null) return false;
        String sql = "SELECT is_bot FROM bid_transactions WHERE auction_id = ? AND bidder_id = ? ORDER BY bid_time DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            ps.setString(2, previousWinnerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("is_bot") == 1;
            }
        } catch (SQLException e) {
            log.error("Failed to evaluate chronological bot identity context: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Executes an atomic Compare-And-Swap (CAS) state evaluation and bid placement sequence.
     * Enforces tight isolation constraints and updates system financial ledgers within a single transaction boundary.
     *
     * @param conn        the active transactional database connection handle
     * @param auctionId   the unique identity token of the target auction aggregate root
     * @param currentUser the identity actor committing the bid request
     * @param newMaxBid   the maximum monetary cap allocation set for evaluation
     * @param isBot       flag signaling whether the operational call originates from automated proxy networks
     * @return the verified {@link BidCommitResult} state changes tuple, or {@code null} if a synchronization race occurs
     * @throws InsufficientFundsException if the bidder's ledger allocation fails liquidity validations
     * @throws SQLException                on low-level persistence mapping crashes
     */
    public BidCommitResult executeBidTransactionSourceOfTruth(Connection conn, String auctionId, User currentUser, long newMaxBid, boolean isBot) throws SQLException, InsufficientFundsException {
        final String selectSql = "SELECT starting_price, current_price, highest_max_bid, "
                + "bid_increment, start_time, end_time, duration_minutes, "
                + "status, winning_bidder_id, seller_id, item_type, item_name, description "
                + "FROM auctions WHERE id = ?";

        long startingPrice, currentPrice, highestMaxBid, bidIncrement;
        LocalDateTime startTime;
        LocalDateTime endTime;
        int durationMinutes;
        String status, winningBidderId, sellerId, itemType, itemName, description;

        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                status = rs.getString("status");
                boolean isAcceptable = Auction.STATUS_WAITING_FOR_BID.equals(status) || Auction.STATUS_RUNNING.equals(status);
                if (!isAcceptable) return null;

                startingPrice = rs.getLong("starting_price");
                currentPrice = rs.getLong("current_price");
                highestMaxBid = rs.getLong("highest_max_bid");
                bidIncrement = rs.getLong("bid_increment");
                durationMinutes = rs.getInt("duration_minutes");
                String startTimeStr = rs.getString("start_time");
                startTime = (startTimeStr != null && !startTimeStr.trim().isEmpty()) ? LocalDateTime.parse(startTimeStr) : null;
                winningBidderId = rs.getString("winning_bidder_id");
                sellerId = rs.getString("seller_id");
                itemType = rs.getString("item_type");
                itemName = rs.getString("item_name");
                description = rs.getString("description");

                String endTimeStr = rs.getString("end_time");
                endTime = (endTimeStr != null && !endTimeStr.trim().isEmpty()) ? LocalDateTime.parse(endTimeStr) : null;
            }
        }

        Auction auctionSnapshot = new Auction();
        auctionSnapshot.setId(auctionId);

        Item item = ItemFactory.createItem(itemType, "", itemName, description, startingPrice);
        auctionSnapshot.setItem(item);

        User seller = new User();
        seller.setId(sellerId);
        auctionSnapshot.setSeller(seller);

        auctionSnapshot.setCurrentPrice(currentPrice);
        auctionSnapshot.setHighestMaxBid(highestMaxBid);
        auctionSnapshot.setBidIncrement(bidIncrement);
        auctionSnapshot.setStartTime(startTime);
        auctionSnapshot.setEndTime(endTime);
        auctionSnapshot.setDurationMinutes(durationMinutes);
        auctionSnapshot.setStatus(status);

        if (winningBidderId != null) {
            User winner = new User();
            winner.setId(winningBidderId);
            auctionSnapshot.setWinningBidder(winner);
        }

        Auction.BidResult result = auctionSnapshot.calculateBidResult(currentUser, newMaxBid, !isBot);
        if (result == null) return null;

        String now = LocalDateTime.now().toString();
        long previousHighestMaxBid = highestMaxBid;
        boolean wasPreviousWinnerBot = wasPreviousBidBot(conn, auctionId, winningBidderId);

        if (result.newWinner != null && result.newWinner.getId().equals(currentUser.getId())) {
            if (winningBidderId != null && winningBidderId.equals(currentUser.getId())) {
                long extraAmount = newMaxBid - previousHighestMaxBid;
                if (extraAmount > 0 && !isBot) {
                    if (!walletDAO.lockBalance(conn, currentUser.getId(), extraAmount)) {
                        throw new InsufficientFundsException("Số dư không đủ để nâng mức đặt giá.");
                    }
                    walletDAO.addTransaction(conn, "W-LCK-" + java.util.UUID.randomUUID(), currentUser.getId(), -extraAmount, "Lock incremental bid raise for auction: " + auctionId, now);
                }
            } else {
                if (winningBidderId != null && !wasPreviousWinnerBot) {
                    walletDAO.unlockBalance(conn, winningBidderId, previousHighestMaxBid);
                    walletDAO.addTransaction(conn, "W-UNL-" + java.util.UUID.randomUUID(), winningBidderId, previousHighestMaxBid, "Unlock outbid reserve for auction: " + auctionId, now);
                    server.ServerExtension.ClientManager.sendToUser(
                            winningBidderId,
                            "OUTBID",
                            java.util.Map.of(
                                    "auctionId", auctionId,
                                    "newPrice",  result.newCurrentPrice
                            )
                    );

                }

                if (!isBot) {
                    if (!walletDAO.lockBalance(conn, currentUser.getId(), newMaxBid)) {
                        throw new InsufficientFundsException("Số dư không đủ để đặt giá.");
                    }
                    walletDAO.addTransaction(conn, "W-LCK-" + java.util.UUID.randomUUID(), currentUser.getId(), -newMaxBid, "Lock bid reserve for auction: " + auctionId, now);
                }
            }
        }

        final String insertBidSql = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, bid_time, is_bot) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertBidSql)) {
            ps.setString(1, "BID-" + java.util.UUID.randomUUID());
            ps.setString(2, auctionId);
            ps.setString(3, currentUser.getId());
            ps.setLong(4, result.newCurrentPrice);
            ps.setString(5, now);
            ps.setInt(6, isBot ? 1 : 0);
            ps.executeUpdate();
        }

        if (result.isFirstBid) {
            // The condition (end_time IS NULL OR end_time > ?) forces an inline wall-clock check.
            // This bypasses the AuctionMonitor's 10-second cron sleep interval, causing any post-deadline
            // late bids to match zero mutated rows and safely fail via standard CAS reject routes.
            final String firstBidSql = "UPDATE auctions SET current_price = ?, end_time = ?, winning_bidder_id = ?, highest_max_bid = ?, status = ? "
                    + "WHERE id = ? AND winning_bidder_id IS NULL AND status = ? "
                    + "AND (end_time IS NULL OR end_time > ?)";
            try (PreparedStatement ps = conn.prepareStatement(firstBidSql)) {
                ps.setLong(1, result.newCurrentPrice);
                ps.setString(2, result.newEndTime.toString());
                ps.setString(3, result.newWinner.getId());
                ps.setLong(4, result.newHighestMaxBid);
                ps.setString(5, Auction.STATUS_RUNNING);
                ps.setString(6, auctionId);
                ps.setString(7, Auction.STATUS_WAITING_FOR_BID);
                ps.setString(8, now);
                if (ps.executeUpdate() == 0) return null;
            }
        } else {
            final String updateSql = (winningBidderId == null)
                    ? "UPDATE auctions SET current_price = ?, end_time = ?, winning_bidder_id = ?, highest_max_bid = ? "
                      + "WHERE id = ? AND current_price = ? AND highest_max_bid = ? AND winning_bidder_id IS NULL AND status = ? "
                      + "AND (end_time IS NULL OR end_time > ?)"
                    : "UPDATE auctions SET current_price = ?, end_time = ?, winning_bidder_id = ?, highest_max_bid = ? "
                      + "WHERE id = ? AND current_price = ? AND highest_max_bid = ? AND winning_bidder_id = ? AND status = ? "
                      + "AND (end_time IS NULL OR end_time > ?)";

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setLong(1, result.newCurrentPrice);
                ps.setString(2, result.newEndTime.toString());
                ps.setString(3, result.newWinner != null ? result.newWinner.getId() : null);
                ps.setLong(4, result.newHighestMaxBid);
                ps.setString(5, auctionId);
                ps.setLong(6, currentPrice);
                ps.setLong(7, highestMaxBid);
                if (winningBidderId != null) {
                    ps.setString(8, winningBidderId);
                    ps.setString(9, Auction.STATUS_RUNNING);
                    ps.setString(10, now);
                } else {
                    ps.setString(8, Auction.STATUS_RUNNING);
                    ps.setString(9, now);
                }
                if (ps.executeUpdate() == 0) return null;
            }
        }

        return new BidCommitResult(auctionId, result.newCurrentPrice, result.newHighestMaxBid,
                result.newWinner != null ? result.newWinner.getId() : null, result.newEndTime);
    }

    /**
     * Persists or updates an auto-bid record within the transaction managed by the caller.
     *
     * <p>This method performs no commit, rollback, or connection lifecycle management.
     * The caller is responsible for opening the connection, setting {@code autoCommit=false},
     * and committing or rolling back after all related operations (including
     * {@link model.auction.Auction#registerAutoBid}) have completed atomically.
     *
     * @param conn        the active, caller-owned database connection with autoCommit disabled
     * @param currentUser the bidder submitting the auto-bid
     * @param auction     the target auction aggregate
     * @param maxBid      the maximum amount the bidder authorises the engine to bid
     * @param increment   the step size per automated bid round
     * @return {@code true} if the record was upserted successfully
     * @throws SQLException                              on any database access error
     * @throws AutoBidLockService.InsufficientFundsException if the wallet cannot cover the required lock delta
     */
    public boolean saveAutoBid(Connection conn, User currentUser, Auction auction,
                               long maxBid, long increment)
            throws SQLException, AutoBidLockService.InsufficientFundsException {

        final String checkSql =
                "SELECT max_bid FROM auto_bids WHERE auction_id = ? AND bidder_id = ?";
        final String upsertSql =
                "INSERT OR REPLACE INTO auto_bids "
                        + "(id, auction_id, bidder_id, max_bid, increment_amount, is_active) "
                        + "VALUES (?, ?, ?, ?, ?, 1)";

        long oldMaxBid = 0L;
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, auction.getId());
            ps.setString(2, currentUser.getId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    oldMaxBid = rs.getLong("max_bid");
                }
            }
        }

        // May throw InsufficientFundsException — caller must catch and rollback
        autoBidLockService.applyLockDifference(conn, currentUser, oldMaxBid, maxBid, auction.getId());

        try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
            ps.setString(1, "AB-" + java.util.UUID.randomUUID());
            ps.setString(2, auction.getId());
            ps.setString(3, currentUser.getId());
            ps.setLong(4, maxBid);
            ps.setLong(5, increment);
            ps.executeUpdate();
        }

        return true;
    }

    /**
     * Terminates an active proxy bidding contract for the targeted user context.
     * Evaluates state machine configurations to securely manage asset ledger lock releases.
     *
     * @param currentUser the bidding user entity removing proxy automation
     * @param auction     the target auction configuration container
     * @return true if deactivation completes successfully, false if no active model matches parameters
     * @throws SQLException on database connection integrity failures
     */
    public boolean cancelAutoBid(User currentUser, Auction auction) throws SQLException {
        final String checkAutoBidSql = "SELECT max_bid FROM auto_bids WHERE auction_id = ? AND bidder_id = ? AND is_active = 1";
        final String checkWinnerSql = "SELECT winning_bidder_id, current_price FROM auctions WHERE id = ?";
        final String deactivateSql = "UPDATE auto_bids SET is_active = 0 WHERE auction_id = ? AND bidder_id = ?";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long currentMaxBid = 0;
                try (PreparedStatement ps = conn.prepareStatement(checkAutoBidSql)) {
                    ps.setString(1, auction.getId());
                    ps.setString(2, currentUser.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        currentMaxBid = rs.getLong("max_bid");
                    }
                }

                boolean isCurrentWinner = false;
                try (PreparedStatement ps = conn.prepareStatement(checkWinnerSql)) {
                    ps.setString(1, auction.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String winningBidderId = rs.getString("winning_bidder_id");
                            isCurrentWinner = currentUser.getId().equals(winningBidderId);
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(deactivateSql)) {
                    ps.setString(1, auction.getId());
                    ps.setString(2, currentUser.getId());
                    ps.executeUpdate();
                }

                if (isCurrentWinner) {
                    log.warn("Deactivated automation for current leading bidder {}. Locked collateral boundary retained.", currentUser.getId());
                } else {
                    autoBidLockService.releaseAllLocks(conn, currentUser, currentMaxBid, auction.getId());
                    log.info("Released non-leading auto-bid structural reserves for user {} on auction {}.", currentUser.getId(), auction.getId());
                }

                conn.commit();
                return true;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Compiles a chronological timeline map array containing historic bid values for a single auction.
     *
     * @param auctionId the target resource identifier key
     * @return a structured timeline array matching the query criteria
     * @throws SQLException on low-level system mapping failures
     */
    public List<Map<String, Object>> getTransactionsForAuction(String auctionId) throws SQLException {
        final String sql = "SELECT bid_amount, bid_time FROM bid_transactions WHERE auction_id = ? ORDER BY bid_time ASC";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("bid_amount", rs.getLong("bid_amount"));
                    row.put("bid_time", rs.getString("bid_time"));
                    list.add(row);
                }
            }
        }
        return list;
    }
}