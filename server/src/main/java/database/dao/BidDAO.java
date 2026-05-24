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

/**
 * Data Access Object managing low-level bidding entries, ledger transactional commits,
 * and proxy wallet balance holding checkpoints.
 */
public class BidDAO {

    private final WalletDAO walletDAO = new WalletDAO();
    private AutoBidLockService autoBidLockService = new AutoBidLockService(walletDAO);

    public void setBidLockService(AutoBidLockService service) {
        this.autoBidLockService = service;
    }

    public static final class BidCommitResult {
        public final String auctionId;
        public final double newCurrentPrice;
        public final double newHighestMaxBid;
        public final String newWinnerId;
        public final LocalDateTime newEndTime;

        public BidCommitResult(String auctionId, long newCurrentPrice, long newHighestMaxBid, String newWinnerId, LocalDateTime newEndTime) {
            this.auctionId = auctionId;
            this.newCurrentPrice = newCurrentPrice;
            this.newHighestMaxBid = newHighestMaxBid;
            this.newWinnerId = newWinnerId;
            this.newEndTime = newEndTime;
        }
    }

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
            // Default conservative mapping strategy if trace lookup drops
        }
        return false;
    }

    /**
     * Executes an atomic Compare-And-Swap (CAS) bidding script payload inside a shared transaction boundary.
     *
     * @param conn shared database transaction connection resource.
     * @param auctionId unique primary identity pointer of the targeted auction.
     * @param currentUser verified entity constructing the bid payload request.
     * @param newMaxBid top monetary ceiling cap evaluated for registration.
     * @param isBot flags if context is parsed from proxy engines or interactive views.
     * @return verified {@link BidCommitResult} instance snapshot data, or null on locking collisions.
     * @throws InsufficientFundsException if the active asset wallet cannot safely buffer the pledge bounds.
     * @throws SQLException on persistence data query rejections.
     */
    public BidCommitResult executeBidTransactionSourceOfTruth(Connection conn, String auctionId, User currentUser, long newMaxBid, boolean isBot) throws SQLException, InsufficientFundsException {

        // SQLite does not support row-level 'FOR UPDATE' locks; state consistency is enforced
        // through direct field constraints during the final atomized conditional UPDATE statements.
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
                startTime = LocalDateTime.parse(rs.getString("start_time"));
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

        Auction.BidResult result = auctionSnapshot.calculateBidResult(currentUser, newMaxBid);
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
            final String firstBidSql = "UPDATE auctions SET current_price = ?, end_time = ?, winning_bidder_id = ?, highest_max_bid = ?, status = ? WHERE id = ? AND winning_bidder_id IS NULL AND status = ?";
            try (PreparedStatement ps = conn.prepareStatement(firstBidSql)) {
                ps.setLong(1, result.newCurrentPrice);
                ps.setString(2, result.newEndTime.toString());
                ps.setString(3, result.newWinner.getId());
                ps.setLong(4, result.newHighestMaxBid);
                ps.setString(5, Auction.STATUS_RUNNING);
                ps.setString(6, auctionId);
                ps.setString(7, Auction.STATUS_WAITING_FOR_BID);
                if (ps.executeUpdate() == 0) return null;
            }
        } else {
            final String updateSql = (winningBidderId == null)
                    ? "UPDATE auctions SET current_price = ?, end_time = ?, winning_bidder_id = ?, highest_max_bid = ? WHERE id = ? AND current_price = ? AND highest_max_bid = ? AND winning_bidder_id IS NULL AND status = ?"
                    : "UPDATE auctions SET current_price = ?, end_time = ?, winning_bidder_id = ?, highest_max_bid = ? WHERE id = ? AND current_price = ? AND highest_max_bid = ? AND winning_bidder_id = ? AND status = ?";

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
                } else {
                    ps.setString(8, Auction.STATUS_RUNNING);
                }
                if (ps.executeUpdate() == 0) return null;
            }
        }

        return new BidCommitResult(auctionId, result.newCurrentPrice, result.newHighestMaxBid, result.newWinner != null ? result.newWinner.getId() : null, result.newEndTime);
    }

    public boolean saveAutoBid(User currentUser, Auction auction, long maxBid, long increment) throws SQLException {
        final String checkSql = "SELECT max_bid FROM auto_bids WHERE auction_id = ? AND bidder_id = ?";
        final String upsertSql = "INSERT OR REPLACE INTO auto_bids (id, auction_id, bidder_id, max_bid, increment_amount, is_active) VALUES (?, ?, ?, ?, ?, 1)";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long oldMaxBid = 0;
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setString(1, auction.getId());
                    ps.setString(2, currentUser.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) oldMaxBid = rs.getLong("max_bid");
                    }
                }

                autoBidLockService.applyLockDifference(conn, currentUser, oldMaxBid, maxBid, auction.getId());

                try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                    ps.setString(1, "AB-" + java.util.UUID.randomUUID());
                    ps.setString(2, auction.getId());
                    ps.setString(3, currentUser.getId());
                    ps.setLong(4, maxBid);
                    ps.setLong(5, increment);
                    ps.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (AutoBidLockService.InsufficientFundsException e) {
                conn.rollback();
                return false;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public boolean cancelAutoBid(User currentUser, Auction auction) throws SQLException {
        final String checkSql = "SELECT max_bid FROM auto_bids WHERE auction_id = ? AND bidder_id = ? AND is_active = 1";
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
                            return false;
                        }
                        currentMaxBid = rs.getLong("max_bid");
                    }
                }

                autoBidLockService.releaseAllLocks(conn, currentUser, currentMaxBid, auction.getId());

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