package database.dao;

import model.auction.Auction;
import model.user.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class BidDAO {

    public boolean executeBidTransaction(Connection conn, User currentUser, Auction auction, double newMaxBid, User previousWinner, double amountToRefund) throws SQLException {
        // STEP 1: Atomic wallet deduction with balance check
        String deductWalletSql = "UPDATE wallets SET balance = balance - ? WHERE user_id = ? AND balance >= ?";
        try (PreparedStatement pstmt = conn.prepareStatement(deductWalletSql)) {
            pstmt.setDouble(1, newMaxBid);
            pstmt.setString(2, currentUser.getId());
            pstmt.setDouble(3, newMaxBid);
            if (pstmt.executeUpdate() == 0) {
                return false; // Insufficient balance
            }
        }

        // STEP 2: Log the withdrawal transaction
        String now = LocalDateTime.now().toString();
        String insertTxnSql = "INSERT INTO wallet_transactions (id, user_id, amount, description, created_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertTxnSql)) {
            pstmt.setString(1, "W-OUT-" + System.currentTimeMillis());
            pstmt.setString(2, currentUser.getId());
            pstmt.setDouble(3, -newMaxBid);
            pstmt.setString(4, "Auction bid placed for session: " + auction.getId());
            pstmt.setString(5, now);
            pstmt.executeUpdate();
        }

        // STEP 3: Refund the previous winner (if not the same user)
        if (previousWinner != null && !previousWinner.getId().equals(currentUser.getId())) {
            String refundSql = "UPDATE wallets SET balance = balance + ? WHERE user_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(refundSql)) {
                pstmt.setDouble(1, amountToRefund);
                pstmt.setString(2, previousWinner.getId());
                pstmt.executeUpdate();
            }

            // Log the refund transaction
            try (PreparedStatement pstmt = conn.prepareStatement(insertTxnSql)) {
                pstmt.setString(1, "W-REF-" + System.currentTimeMillis());
                pstmt.setString(2, previousWinner.getId());
                pstmt.setDouble(3, amountToRefund);
                pstmt.setString(4, "Refund for price overrun during session: " + auction.getId());
                pstmt.setString(5, now);
                pstmt.executeUpdate();
            }
        }

        // STEP 4: Record the bid history
        String bidLogSql = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, bid_time) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(bidLogSql)) {
            pstmt.setString(1, "BID-" + System.currentTimeMillis());
            pstmt.setString(2, auction.getId());
            pstmt.setString(3, currentUser.getId());
            pstmt.setDouble(4, auction.getCurrentPrice());
            pstmt.setString(5, now);
            pstmt.executeUpdate();
        }

        // STEP 5: Update auction current price
        String updateAuctionSql = "UPDATE auctions SET current_price = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(updateAuctionSql)) {
            pstmt.setDouble(1, auction.getCurrentPrice());
            pstmt.setString(2, auction.getId());
            pstmt.executeUpdate();
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
