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

        public BidCommitResult(String auctionId, double newCurrentPrice, double newHighestMaxBid, String newWinnerId, LocalDateTime newEndTime) {
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
     * {@link Auction#calculateBidResult(User, double)}, and commits the final state with optimistic locking.
     *
     * @return a {@link BidCommitResult} on success, or {@code null} if validation fails or optimistic lock conflicts.
     */
    public BidCommitResult executeBidTransactionSourceOfTruth(Connection conn, String auctionId, User currentUser, double newMaxBid, double expectedCurrentPrice) throws SQLException {
        String selectSql = "SELECT starting_price, current_price, highest_max_bid, bid_increment, end_time, status, winning_bidder_id " +
                "FROM auctions WHERE id = ?";

        double startingPrice;
        double currentPrice;
        double highestMaxBid;
        double bidIncrement;
        LocalDateTime endTime;
        String status;
        String winningBidderId;

        try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
            selectStmt.setString(1, auctionId);
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                startingPrice = rs.getDouble("starting_price");
                currentPrice = rs.getDouble("current_price");
                if (Double.compare(currentPrice, expectedCurrentPrice) != 0) {
                    return null;
                }
                highestMaxBid = rs.getDouble("highest_max_bid");
                bidIncrement = rs.getDouble("bid_increment");
                endTime = LocalDateTime.parse(rs.getString("end_time"));
                status = rs.getString("status");
                winningBidderId = rs.getString("winning_bidder_id"); // nullable
            }
        }

        // Build a minimal Auction snapshot for Model-calculation (MVC-compliant: core rules remain in Model).
        Auction auctionSnapshot = new Auction();
        Item item = new Item();
        item.setStartingPrice(startingPrice);
        auctionSnapshot.setItem(item);
        auctionSnapshot.setCurrentPrice(currentPrice);
        auctionSnapshot.setHighestMaxBid(highestMaxBid);
        auctionSnapshot.setBidIncrement(bidIncrement);
        auctionSnapshot.setEndTime(endTime);
        auctionSnapshot.setStatus(status);
        // maxEndTime is not persisted; keep it non-null to avoid NPE and preserve "hard-cap" behavior locally.
        auctionSnapshot.setMaxEndTime(endTime.plusMinutes(30));

        if (winningBidderId != null) {
            User winner = new User();
            winner.setId(winningBidderId);
            auctionSnapshot.setWinningBidder(winner);
        }

        Auction.BidResult result = auctionSnapshot.calculateBidResult(currentUser, newMaxBid);
        if (result == null) {
            return null;
        }

        // Wallet logic needs previous state (from DB, not RAM).
        User previousWinner = null;
        if (winningBidderId != null) {
            previousWinner = new User();
            previousWinner.setId(winningBidderId);
        }
        double previousHighestMaxBid = highestMaxBid;

        // STEP 1: Handle wallet transactions
        String now = LocalDateTime.now().toString();
        if (result.newWinner != null && result.newWinner.getId().equals(currentUser.getId())) {
            if (previousWinner != null && previousWinner.getId().equals(currentUser.getId())) {
                double amountToDeduct = newMaxBid - previousHighestMaxBid;
                if (amountToDeduct > 0) {
                    if (!walletDAO.deductBalance(conn, currentUser.getId(), amountToDeduct)) return null;
                    walletDAO.addTransaction(
                            conn,
                            "W-INC-" + System.currentTimeMillis(),
                            currentUser.getId(),
                            -amountToDeduct,
                            "Incremental auction bid for session: " + auctionId,
                            now
                    );
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
                if (!walletDAO.deductBalance(conn, currentUser.getId(), newMaxBid)) return null;
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

        // STEP 2: Record the bid history
        String bidLogSql = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, bid_time) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(bidLogSql)) {
            pstmt.setString(1, "BID-" + System.currentTimeMillis());
            pstmt.setString(2, auctionId);
            pstmt.setString(3, currentUser.getId());
            pstmt.setDouble(4, result.newCurrentPrice);
            pstmt.setString(5, now);
            pstmt.executeUpdate();
        }

        // STEP 3: Update auction with optimistic locking (DB source-of-truth)
        final String updateAuctionSql;
        if (winningBidderId == null) {
            updateAuctionSql = "UPDATE auctions SET current_price = ?, end_time = ?, winning_bidder_id = ?, highest_max_bid = ? " +
                    "WHERE id = ? AND current_price = ? AND highest_max_bid = ? AND winning_bidder_id IS NULL";
        } else {
            updateAuctionSql = "UPDATE auctions SET current_price = ?, end_time = ?, winning_bidder_id = ?, highest_max_bid = ? " +
                    "WHERE id = ? AND current_price = ? AND highest_max_bid = ? AND winning_bidder_id = ?";
        }

        try (PreparedStatement pstmt = conn.prepareStatement(updateAuctionSql)) {
            pstmt.setDouble(1, result.newCurrentPrice);
            pstmt.setString(2, result.newEndTime.toString());
            pstmt.setString(3, result.newWinner != null ? result.newWinner.getId() : null);
            pstmt.setDouble(4, result.newHighestMaxBid);
            pstmt.setString(5, auctionId);
            pstmt.setDouble(6, currentPrice);
            pstmt.setDouble(7, highestMaxBid);
            if (winningBidderId != null) {
                pstmt.setString(8, winningBidderId);
            }

            if (pstmt.executeUpdate() == 0) {
                // Conflict: another transaction changed the auction state after we read it.
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

    public boolean executeBidTransaction(Connection conn, User currentUser, double newMaxBid, User previousWinner, double previousHighestMaxBid, User newWinner, double newHighestMaxBid, double newCurrentPrice, String auctionId, LocalDateTime endTime, double currentPriceInDB) throws SQLException {
        // STEP 1: Handle wallet transactions
        String now = LocalDateTime.now().toString();

        // ONLY lock money IF the currentUser actually becomes the NEW winning bidder
        if (newWinner != null && newWinner.getId().equals(currentUser.getId())) {
            
            if (previousWinner != null && previousWinner.getId().equals(currentUser.getId())) {
                // Case 1: User is outbidding themselves. Only deduct the difference.
                double amountToDeduct = newMaxBid - previousHighestMaxBid;
                if (amountToDeduct > 0) {
                    if (!walletDAO.deductBalance(conn, currentUser.getId(), amountToDeduct)) return false; // Insufficient balance

                    // Log the incremental withdrawal
                    walletDAO.addTransaction(
                            conn,
                            "W-INC-" + System.currentTimeMillis(),
                            currentUser.getId(),
                            -amountToDeduct,
                            "Incremental auction bid for session: " + auctionId,
                            now
                    );
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
                if (!walletDAO.deductBalance(conn, currentUser.getId(), newMaxBid)) return false; // Insufficient balance

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
            pstmt.setDouble(4, newCurrentPrice);
            pstmt.setString(5, now);
            pstmt.executeUpdate();
        }

        // STEP 3: Update auction's current price, end_time, and winner info in DB
        // We use Optimistic Locking by checking if the current_price has changed since we last read it in RAM.
        String updateAuctionSql = "UPDATE auctions SET current_price = ?, end_time = ?, winning_bidder_id = ?, highest_max_bid = ? WHERE id = ? AND current_price = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(updateAuctionSql)) {
            pstmt.setDouble(1, newCurrentPrice);
            pstmt.setString(2, endTime.toString());
            pstmt.setString(3, newWinner != null ? newWinner.getId() : null);
            pstmt.setDouble(4, newHighestMaxBid);
            pstmt.setString(5, auctionId);
            pstmt.setDouble(6, currentPriceInDB); // THE KEY: Optimistic Locking condition
            
            if (pstmt.executeUpdate() == 0) {
                // If 0 rows updated, it means another thread changed current_price in the meantime.
                System.out.println("[BidDAO]: Conflict detected! Current price in DB is different from " + currentPriceInDB);
                return false; 
            }
        }

        return true;
    }

    public boolean saveAutoBid(User currentUser, Auction auction, double maxBid, double increment) throws SQLException {
        String sql = "INSERT OR REPLACE INTO auto_bids (id, auction_id, bidder_id, max_bid, increment_amount, is_active) VALUES (?, ?, ?, ?, ?, 1)";
        try (Connection conn = database.DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "AB-" + System.currentTimeMillis());
            pstmt.setString(2, auction.getId());
            pstmt.setString(3, currentUser.getId());
            pstmt.setDouble(4, maxBid);
            pstmt.setDouble(5, increment);
            return pstmt.executeUpdate() > 0;
        }
    }
}