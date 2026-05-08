package database.dao;

import model.auction.Auction;
import model.item.Item;
import model.user.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class BidDAO {
    private final WalletDAO walletDAO = new WalletDAO();

    public static final class BidCommitResult {
        public final String auctionId;
        public final double newCurrentPrice;
        public final double newHighestMaxBid;
        public final String newWinnerId; // nullable
        public final LocalDateTime newEndTime;

        public BidCommitResult(String auctionId, long newCurrentPrice, long newHighestMaxBid, String newWinnerId, LocalDateTime newEndTime) {
            this.auctionId = auctionId;
            this.newCurrentPrice = newCurrentPrice;
            this.newHighestMaxBid = newHighestMaxBid;
            this.newWinnerId = newWinnerId;
            this.newEndTime = newEndTime;
        }
    }

    /**
     * Executes a bid transaction using the database as the single source of truth.
     * <p>
     * This method reads the auction state inside the transaction, computes the bid outcome using
     * {@link Auction#calculateBidResult(User, long)}, and commits the final state with optimistic locking.
     *
     * @return a {@link BidCommitResult} on success, or {@code null} if validation fails or optimistic lock conflicts.
     */
    // Thêm hàm helper này vào BidDAO.java
    /**
     * Kiểm tra xem một user có đang kích hoạt Auto-Bid trong phiên đấu giá này không.
     */


    /**
     * Truy vấn lịch sử giao dịch để biết chính xác lượt bid cũ là do Người hay Bot đặt.
     */
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

    // ================= HÀM XỬ LÝ CHÍNH ĐÃ FIX =================

    /**
     * Executes a bid transaction using the database as the single source of truth.
     * Đã Fix: Thống nhất cơ chế Locking Funds cho cả Human và Bot.
     */
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

        String selectSql = "SELECT starting_price, current_price, highest_max_bid, bid_increment, end_time, status, winning_bidder_id " +
                "FROM auctions WHERE id = ?";
        long startingPrice;
        long currentPrice;
        long highestMaxBid;
        long bidIncrement;
        LocalDateTime endTime;
        String status;
        String winningBidderId;

        try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
            selectStmt.setString(1, auctionId);
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (!rs.next()) return null;
                startingPrice = rs.getLong("starting_price");
                currentPrice = rs.getLong("current_price");
                highestMaxBid = rs.getLong("highest_max_bid");
                bidIncrement = rs.getLong("bid_increment");
                endTime = LocalDateTime.parse(rs.getString("end_time"));
                status = rs.getString("status");
                winningBidderId = rs.getString("winning_bidder_id");
            }
        }

        Auction auctionSnapshot = new Auction();
        Item item = new Item();
        item.setStartingPrice(startingPrice);
        auctionSnapshot.setItem(item);
        auctionSnapshot.setCurrentPrice(currentPrice);
        auctionSnapshot.setHighestMaxBid(highestMaxBid);
        auctionSnapshot.setBidIncrement(bidIncrement);
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
                        // THAY ĐỔI 1: Chỉ LOCK tiền từ Balance, KHÔNG deduct thẳng
                        if (!walletDAO.lockBalance(conn, currentUser.getId(), amountToDeduct)) return null;
                        walletDAO.addTransaction(conn, "W-LCK-" + java.util.UUID.randomUUID().toString(), currentUser.getId(), -newMaxBid, "Lock funds for auction bid: " + auctionId, now);
                    }
                }
            } else {
                if (previousWinner != null) {
                    // THAY ĐỔI 2: Dựa vào Lịch sử thực tế để Unlock thay vì trạng thái Bot hiện tại
                    if (!wasPreviousWinnerBot) {
                        walletDAO.unlockBalance(conn, previousWinner.getId(), previousHighestMaxBid);
                        walletDAO.addTransaction(conn, "W-UNL-" + java.util.UUID.randomUUID().toString(), previousWinner.getId(), previousHighestMaxBid, "Unlock funds (outbid) in session: " + auctionId, now);
                    }
                }

                if (!isBot) {
                    // THAY ĐỔI 1: Chỉ LOCK tiền từ Balance
                    if (!walletDAO.lockBalance(conn, currentUser.getId(), newMaxBid)) return null;
                    walletDAO.addTransaction(conn, "W-LCK-" + System.currentTimeMillis(), currentUser.getId(), -newMaxBid, "Lock funds for auction bid: " + auctionId, now);
                }
            }
        }

        // STEP 2: Record the bid history with is_bot flag
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
        if (expectedWinnerId == null) {
            updateAuctionSql = "UPDATE auctions SET current_price = ?, end_time = ?, winning_bidder_id = ?, highest_max_bid = ? " +
                    "WHERE id = ? AND current_price = ? AND highest_max_bid = ? AND winning_bidder_id IS NULL";
        } else {
            updateAuctionSql = "UPDATE auctions SET current_price = ?, end_time = ?, winning_bidder_id = ?, highest_max_bid = ? " +
                    "WHERE id = ? AND current_price = ? AND highest_max_bid = ? AND winning_bidder_id = ?";
        }

        try (PreparedStatement pstmt = conn.prepareStatement(updateAuctionSql)) {
            pstmt.setLong(1, result.newCurrentPrice);
            pstmt.setString(2, result.newEndTime.toString());
            pstmt.setString(3, result.newWinner != null ? result.newWinner.getId() : null);
            pstmt.setLong(4, result.newHighestMaxBid);
            pstmt.setString(5, auctionId);
            pstmt.setLong(6, expectedPrice);
            pstmt.setLong(7, expectedMaxBid);
            if (expectedWinnerId != null) {
                pstmt.setString(8, expectedWinnerId);
            }

            if (pstmt.executeUpdate() == 0) {
                return null; // Optimistic lock conflict
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
        // STEP 1: Handle wallet transactions
        String now = LocalDateTime.now().toString();

        // ONLY lock money IF the currentUser actually becomes the NEW winning bidder
        if (newWinner != null && newWinner.getId().equals(currentUser.getId())) {

            if (previousWinner != null && previousWinner.getId().equals(currentUser.getId())) {
                // Case 1: User is outbidding themselves. Only deduct the difference.
                long amountToDeduct = newMaxBid - previousHighestMaxBid;
                if (amountToDeduct > 0) {
                    if (!walletDAO.deductBalance(conn, currentUser.getId(), amountToDeduct))
                        return false; // Insufficient balance

                    // Log the incremental withdrawal
                    walletDAO.addTransaction(conn, "W-LCK-" + java.util.UUID.randomUUID().toString(), currentUser.getId(), -amountToDeduct, "Lock incremental funds for session: " + auctionId, now);
                }
            } else {
                // Case 2: A new user is bidding and becoming the winner.
                // Refund previous winner
                if (previousWinner != null) {
                    walletDAO.updateBalance(conn, previousWinner.getId(), previousHighestMaxBid);

                    // Log the refund
                    walletDAO.addTransaction(
                            conn,
                            "W-REF-" + System.currentTimeMillis(),
                            previousWinner.getId(),
                            previousHighestMaxBid,
                            "Refund for being outbid in session: " + auctionId,
                            now
                    );
                }

                // Deduct full amount from new bidder
                if (!walletDAO.deductBalance(conn, currentUser.getId(), newMaxBid))
                    return false; // Insufficient balance

                // Log the full withdrawal
                walletDAO.addTransaction(
                        conn,
                        "W-OUT-" + System.currentTimeMillis(),
                        currentUser.getId(),
                        -newMaxBid,
                        "Auction bid placed for session: " + auctionId,
                        now
                );
            }
        } else {
            // Case 3: currentUser bid but LOST (previousWinner's max bid was higher)
            // NO money is deducted from currentUser.
            // NO refund for previousWinner (they are still winning).
            System.out.println("[BidDAO]: User " + currentUser.getUserName() + " bid but lost to current winner's max bid. No money deducted.");
        }

        // STEP 2: Record the bid history (Every bid attempt that changes current price should be logged)
        String bidLogSql = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, bid_time) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(bidLogSql)) {
            pstmt.setString(1, "BID-" + System.currentTimeMillis());
            pstmt.setString(2, auctionId);
            pstmt.setString(3, currentUser.getId());
            pstmt.setLong(4, newCurrentPrice);
            pstmt.setString(5, now);
            pstmt.executeUpdate();
        }

        // STEP 3: Update auction's current price, end_time, and winner info in DB
        // We use Optimistic Locking by checking if the current_price has changed since we last read it in RAM.
        String updateAuctionSql = "UPDATE auctions SET current_price = ?, end_time = ?, winning_bidder_id = ?, highest_max_bid = ? WHERE id = ? AND current_price = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(updateAuctionSql)) {
            pstmt.setLong(1, newCurrentPrice);
            pstmt.setString(2, endTime.toString());
            pstmt.setString(3, newWinner != null ? newWinner.getId() : null);
            pstmt.setLong(4, newHighestMaxBid);
            pstmt.setString(5, auctionId);
            pstmt.setLong(6, currentPriceInDB); // THE KEY: Optimistic Locking condition

            if (pstmt.executeUpdate() == 0) {
                // If 0 rows updated, it means another thread changed current_price in the meantime.
                System.out.println("[BidDAO]: Conflict detected! Current price in DB is different from " + currentPriceInDB);
                return false;
            }
        }

        return true;
    }

    // Thay thế saveAutoBid trong BidDAO.java [cite: 458]
    public boolean saveAutoBid(User currentUser, Auction auction, long maxBid, long increment) throws SQLException {
        String checkSql = "SELECT max_bid FROM auto_bids WHERE auction_id = ? AND bidder_id = ?";
        String upsertSql = "INSERT OR REPLACE INTO auto_bids (id, auction_id, bidder_id, max_bid, increment_amount, is_active) VALUES (?, ?, ?, ?, ?, 1)";

        try (Connection conn = database.DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long oldMaxBid = 0;
                // 1. Kiểm tra xem đã có cấu hình cũ chưa để tính chênh lệch
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setString(1, auction.getId());
                    checkStmt.setString(2, currentUser.getId());
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next()) oldMaxBid = rs.getLong("max_bid");
                    }
                }

                long difference = maxBid - oldMaxBid;
                if (difference > 0) {
                    // Khóa thêm tiền nếu tăng maxBid
                    if (!walletDAO.lockBalance(conn, currentUser.getId(), difference)) {
                        conn.rollback();
                        return false; // Không đủ tiền khả dụng
                    }
                } else if (difference < 0) {
                    // Hoàn lại tiền nếu giảm maxBid
                    walletDAO.unlockBalance(conn, currentUser.getId(), Math.abs(difference));
                }

                // 2. Lưu cấu hình bot
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
}