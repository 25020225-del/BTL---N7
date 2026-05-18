package database.dao;

import database.DatabaseManager;
import model.auction.Auction;
import model.item.Item;
import model.user.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BidDAO {
    private final WalletDAO walletDAO = new WalletDAO();

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
                if (rs.next()) {
                    return rs.getInt("is_bot") == 1;
                }
            }
        } catch (SQLException e) {
            // Fallback an toàn nếu lỗi hoặc cột chưa kịp tạo
        }
        return false;
    }

    public BidCommitResult executeBidTransactionSourceOfTruth(
            Connection conn,
            String auctionId,
            User currentUser,
            long newMaxBid,
            long expectedPrice,
            long expectedMaxBid,
            String expectedWinnerId,
            boolean isBot
    ) throws SQLException {

        String selectSql = "SELECT starting_price, current_price, highest_max_bid, bid_increment, start_time, end_time, status, winning_bidder_id, seller_id " +
                "FROM auctions WHERE id = ?";

        long startingPrice, currentPrice, highestMaxBid, bidIncrement;
        LocalDateTime startTime, endTime;
        String status, winningBidderId, sellerId;

        try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
            selectStmt.setString(1, auctionId);
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (!rs.next()) return null;

                status = rs.getString("status");
                // [ARCHITECT FIX]: LỚP PHÒNG THỦ 2 - Hủy giao dịch nếu DB báo phiên không còn RUNNING
                if (!Auction.STATUS_RUNNING.equals(status)) {
                    return null;
                }

                startingPrice = rs.getLong("starting_price");
                currentPrice = rs.getLong("current_price");
                highestMaxBid = rs.getLong("highest_max_bid");
                bidIncrement = rs.getLong("bid_increment");
                startTime = LocalDateTime.parse(rs.getString("start_time"));
                endTime = LocalDateTime.parse(rs.getString("end_time"));
                winningBidderId = rs.getString("winning_bidder_id");
                sellerId = rs.getString("seller_id");
            }
        }

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

        User previousWinner = null;
        if (winningBidderId != null) {
            previousWinner = new User();
            previousWinner.setId(winningBidderId);
        }
        long previousHighestMaxBid = highestMaxBid;

        boolean wasPreviousWinnerBot = wasPreviousBidBot(conn, auctionId, winningBidderId);

        // STEP 1: Handle wallet transactions
        String now = LocalDateTime.now().toString();

        if (result.newWinner != null && result.newWinner.getId().equals(currentUser.getId())) {
            if (previousWinner != null && previousWinner.getId().equals(currentUser.getId())) {
                long amountToDeduct = newMaxBid - previousHighestMaxBid;
                if (amountToDeduct > 0) {
                    if (!isBot) {
                        if (!walletDAO.lockBalance(conn, currentUser.getId(), amountToDeduct)) {
                            throw new InsufficientFundsException("Số dư không đủ");
                        }
                        walletDAO.addTransaction(conn, "W-LCK-" + java.util.UUID.randomUUID().toString(), currentUser.getId(), -amountToDeduct, "Lock incremental funds for session: " + auctionId, now);
                    }
                }
            } else {
                if (previousWinner != null) {
                    if (!wasPreviousWinnerBot) {
                        walletDAO.unlockBalance(conn, previousWinner.getId(), previousHighestMaxBid);
                        walletDAO.addTransaction(conn, "W-UNL-" + java.util.UUID.randomUUID().toString(), previousWinner.getId(), previousHighestMaxBid, "Unlock funds (outbid) in session: " + auctionId, now);
                    }
                }

                if (!isBot) {
                    if (!walletDAO.lockBalance(conn, currentUser.getId(), newMaxBid)) {
                        throw new InsufficientFundsException("Số dư không đủ");
                    }
                    walletDAO.addTransaction(conn, "W-LCK-" + java.util.UUID.randomUUID().toString(), currentUser.getId(), -newMaxBid, "Lock funds for auction bid: " + auctionId, now);
                }
            }
        }

        // STEP 2: Record the bid history
        String bidLogSql = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, bid_time, is_bot) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(bidLogSql)) {
            pstmt.setString(1, "BID-" + java.util.UUID.randomUUID().toString());
            pstmt.setString(2, auctionId);
            pstmt.setString(3, currentUser.getId());
            pstmt.setLong(4, result.newCurrentPrice);
            pstmt.setString(5, now);
            pstmt.setInt(6, isBot ? 1 : 0);
            pstmt.executeUpdate();
        }

        // STEP 3: Update auction with optimistic locking
        final String updateAuctionSql;
        // [ARCHITECT FIX]: LỚP PHÒNG THỦ 3 - Chặn ghi đè bằng ACID Condition (AND status = 'RUNNING')
        if (winningBidderId == null) {
            updateAuctionSql = "UPDATE auctions SET current_price = ?, end_time = ?, winning_bidder_id = ?, highest_max_bid = ? " +
                    "WHERE id = ? AND current_price = ? AND highest_max_bid = ? AND winning_bidder_id IS NULL AND status = 'RUNNING'";
        } else {
            updateAuctionSql = "UPDATE auctions SET current_price = ?, end_time = ?, winning_bidder_id = ?, highest_max_bid = ? " +
                    "WHERE id = ? AND current_price = ? AND highest_max_bid = ? AND winning_bidder_id = ? AND status = 'RUNNING'";
        }

        try (PreparedStatement pstmt = conn.prepareStatement(updateAuctionSql)) {
            pstmt.setLong(1, result.newCurrentPrice);
            pstmt.setString(2, result.newEndTime.toString());
            pstmt.setString(3, result.newWinner != null ? result.newWinner.getId() : null);
            pstmt.setLong(4, result.newHighestMaxBid);
            pstmt.setString(5, auctionId);
            pstmt.setLong(6, currentPrice);
            pstmt.setLong(7, highestMaxBid);

            if (winningBidderId != null) {
                pstmt.setString(8, winningBidderId);
            }

            if (pstmt.executeUpdate() == 0) {
                return null;
            }
        }

        return new BidCommitResult(
                auctionId,
                result.newCurrentPrice,
                result.newHighestMaxBid,
                result.newWinner != null ? result.newWinner.getId() : null,
                result.newEndTime
        );
    }

    public boolean executeBidTransaction(Connection conn, User currentUser, long newMaxBid, User previousWinner, long previousHighestMaxBid, User newWinner, long newHighestMaxBid, long newCurrentPrice, String auctionId, LocalDateTime endTime, long currentPriceInDB) throws SQLException {
        String now = LocalDateTime.now().toString();

        if (newWinner != null && newWinner.getId().equals(currentUser.getId())) {
            if (previousWinner != null && previousWinner.getId().equals(currentUser.getId())) {
                long amountToDeduct = newMaxBid - previousHighestMaxBid;
                if (amountToDeduct > 0) {
                    if (!walletDAO.deductBalance(conn, currentUser.getId(), amountToDeduct))
                        return false;
                    walletDAO.addTransaction(conn, "W-LCK-" + java.util.UUID.randomUUID().toString(), currentUser.getId(), -amountToDeduct, "Lock incremental funds for session: " + auctionId, now);
                }
            } else {
                if (previousWinner != null) {
                    walletDAO.updateBalance(conn, previousWinner.getId(), previousHighestMaxBid);
                    walletDAO.addTransaction(
                            conn,
                            "W-REF-" + System.currentTimeMillis(),
                            previousWinner.getId(),
                            previousHighestMaxBid,
                            "Refund for being outbid in session: " + auctionId,
                            now
                    );
                }

                if (!walletDAO.deductBalance(conn, currentUser.getId(), newMaxBid))
                    return false;

                walletDAO.addTransaction(
                        conn,
                        "W-OUT-" + System.currentTimeMillis(),
                        currentUser.getId(),
                        -newMaxBid,
                        "Auction bid placed for session: " + auctionId,
                        now
                );
            }
        }

        String bidLogSql = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, bid_time) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(bidLogSql)) {
            pstmt.setString(1, "BID-" + System.currentTimeMillis());
            pstmt.setString(2, auctionId);
            pstmt.setString(3, currentUser.getId());
            pstmt.setLong(4, newCurrentPrice);
            pstmt.setString(5, now);
            pstmt.executeUpdate();
        }

        // [ARCHITECT FIX]: Chặn ghi đè cho luồng cũ
        String updateAuctionSql = "UPDATE auctions SET current_price = ?, end_time = ?, winning_bidder_id = ?, highest_max_bid = ? WHERE id = ? AND current_price = ? AND status = 'RUNNING'";
        try (PreparedStatement pstmt = conn.prepareStatement(updateAuctionSql)) {
            pstmt.setLong(1, newCurrentPrice);
            pstmt.setString(2, endTime.toString());
            pstmt.setString(3, newWinner != null ? newWinner.getId() : null);
            pstmt.setLong(4, newHighestMaxBid);
            pstmt.setString(5, auctionId);
            pstmt.setLong(6, currentPriceInDB);

            if (pstmt.executeUpdate() == 0) {
                return false;
            }
        }
        return true;
    }

    public boolean saveAutoBid(User currentUser, Auction auction, long maxBid, long increment) throws SQLException {
        String checkSql = "SELECT max_bid FROM auto_bids WHERE auction_id = ? AND bidder_id = ?";
        String upsertSql = "INSERT OR REPLACE INTO auto_bids (id, auction_id, bidder_id, max_bid, increment_amount, is_active) VALUES (?, ?, ?, ?, ?, 1)";
        try (Connection conn = database.DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long oldMaxBid = 0;
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setString(1, auction.getId());
                    checkStmt.setString(2, currentUser.getId());
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next()) oldMaxBid = rs.getLong("max_bid");
                    }
                }

                long difference = maxBid - oldMaxBid;
                if (difference > 0) {
                    if (!walletDAO.lockBalance(conn, currentUser.getId(), difference)) {
                        conn.rollback();
                        return false;
                    }
                } else if (difference < 0) {
                    walletDAO.unlockBalance(conn, currentUser.getId(), Math.abs(difference));
                }

                try (PreparedStatement pstmt = conn.prepareStatement(upsertSql)) {
                    pstmt.setString(1, "AB-" + System.currentTimeMillis());
                    pstmt.setString(2, auction.getId());
                    pstmt.setString(3, currentUser.getId());
                    pstmt.setLong(4, maxBid);
                    pstmt.setLong(5, increment);
                    pstmt.executeUpdate();
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
     * Retrieves all bid transactions for the given auction, ordered chronologically.
     *
     * @param auctionId The ID of the auction to query.
     * @return An ordered list of maps containing {@code bid_amount} and {@code bid_time}.
     * @throws SQLException if the query fails.
     */
    public List<Map<String, Object>> getTransactionsForAuction(String auctionId) throws SQLException {
        String sql = "SELECT bid_amount, bid_time FROM bid_transactions WHERE auction_id = ? ORDER BY bid_time ASC";
        List<Map<String, Object>> list = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auctionId);
            try (ResultSet rs = pstmt.executeQuery()) {
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