package database.dao;

import model.auction.Auction;
import model.user.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class BidDAO {

    public boolean executeBidTransaction(Connection conn, User currentUser, double newMaxBid, User previousWinner, double previousHighestMaxBid, double newCurrentPrice, String auctionId) throws SQLException {
        // STEP 1: Handle wallet transactions
        String now = LocalDateTime.now().toString();
        String insertTxnSql = "INSERT INTO wallet_transactions (id, user_id, amount, description, created_at) VALUES (?, ?, ?, ?, ?)";

        if (previousWinner != null && previousWinner.getId().equals(currentUser.getId())) {
            // Case 1: User is outbidding themselves. Only deduct the difference.
            double amountToDeduct = newMaxBid - previousHighestMaxBid;
            if (amountToDeduct > 0) {
                String deductWalletSql = "UPDATE wallets SET balance = balance - ? WHERE user_id = ? AND balance >= ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deductWalletSql)) {
                    pstmt.setDouble(1, amountToDeduct);
                    pstmt.setString(2, currentUser.getId());
                    pstmt.setDouble(3, amountToDeduct);
                    if (pstmt.executeUpdate() == 0) return false; // Insufficient balance
                }
                // Log the incremental withdrawal
                try (PreparedStatement pstmt = conn.prepareStatement(insertTxnSql)) {
                    pstmt.setString(1, "W-INC-" + System.currentTimeMillis());
                    pstmt.setString(2, currentUser.getId());
                    pstmt.setDouble(3, -amountToDeduct);
                    pstmt.setString(4, "Incremental auction bid for session: " + auctionId);
                    pstmt.setString(5, now);
                    pstmt.executeUpdate();
                }
            }
        } else {
            // Case 2: A new user is bidding.
            // Refund previous winner
            if (previousWinner != null) {
                String refundSql = "UPDATE wallets SET balance = balance + ? WHERE user_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(refundSql)) {
                    pstmt.setDouble(1, previousHighestMaxBid);
                    pstmt.setString(2, previousWinner.getId());
                    pstmt.executeUpdate();
                }
                // Log the refund
                try (PreparedStatement pstmt = conn.prepareStatement(insertTxnSql)) {
                    pstmt.setString(1, "W-REF-" + System.currentTimeMillis());
                    pstmt.setString(2, previousWinner.getId());
                    pstmt.setDouble(3, previousHighestMaxBid);
                    pstmt.setString(4, "Refund for being outbid in session: " + auctionId);
                    pstmt.setString(5, now);
                    pstmt.executeUpdate();
                }
            }

            // Deduct full amount from new bidder
            String deductWalletSql = "UPDATE wallets SET balance = balance - ? WHERE user_id = ? AND balance >= ?";
            try (PreparedStatement pstmt = conn.prepareStatement(deductWalletSql)) {
                pstmt.setDouble(1, newMaxBid);
                pstmt.setString(2, currentUser.getId());
                pstmt.setDouble(3, newMaxBid);
                if (pstmt.executeUpdate() == 0) return false; // Insufficient balance
            }
            // Log the full withdrawal
            try (PreparedStatement pstmt = conn.prepareStatement(insertTxnSql)) {
                pstmt.setString(1, "W-OUT-" + System.currentTimeMillis());
                pstmt.setString(2, currentUser.getId());
                pstmt.setDouble(3, -newMaxBid);
                pstmt.setString(4, "Auction bid placed for session: " + auctionId);
                pstmt.setString(5, now);
                pstmt.executeUpdate();
            }
        }

        // STEP 2: Record the bid history
        String bidLogSql = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, bid_time) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(bidLogSql)) {
            pstmt.setString(1, "BID-" + System.currentTimeMillis());
            pstmt.setString(2, auctionId);
            pstmt.setString(3, currentUser.getId());
            pstmt.setDouble(4, newCurrentPrice);
            pstmt.setString(5, now);
            pstmt.executeUpdate();
        }

        // STEP 3: Update auction's current price in DB
        String updateAuctionSql = "UPDATE auctions SET current_price = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(updateAuctionSql)) {
            pstmt.setDouble(1, newCurrentPrice);
            pstmt.setString(2, auctionId);
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