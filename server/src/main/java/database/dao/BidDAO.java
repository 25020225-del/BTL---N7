package database.dao;

import model.auction.Auction;
import model.user.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class BidDAO {
    private final WalletDAO walletDAO = new WalletDAO();

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