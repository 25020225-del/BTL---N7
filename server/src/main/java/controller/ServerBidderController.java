package controller;

import database.DatabaseManager;
import model.Auction;
import model.Bidder;
import model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class ServerBidderController {

    public static final String ANSI_RESET  = "\u001B[0m";
    public static final String ANSI_RED    = "\u001B[31m";
    public static final String ANSI_GREEN  = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";

    public boolean placeBidOnAuction(User currentUser, Auction auction, double newMaxBid) {
        if (auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("[Security]: You cannot bid on your own auction");
            return false;
        }

        Bidder bidder = new Bidder(currentUser);
        boolean isSuccess = auction.placeBid(bidder, newMaxBid);

        if (isSuccess) {
            String insertTransactionSql = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, bid_time) VALUES (?, ?, ?, ?, ?)";
            String updateAuctionPriceSql = "UPDATE auctions SET current_price = ? WHERE id = ?";

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);

                try {
                    try (PreparedStatement pstmt1 = conn.prepareStatement(insertTransactionSql)) {
                        pstmt1.setString(1, "TXN-" + System.currentTimeMillis());
                        pstmt1.setString(2, auction.getId());
                        pstmt1.setString(3, bidder.getId());
                        pstmt1.setDouble(4, auction.getCurrentPrice());
                        pstmt1.setString(5, LocalDateTime.now().toString());
                        pstmt1.executeUpdate();
                    }

                    try (PreparedStatement pstmt2 = conn.prepareStatement(updateAuctionPriceSql)) {
                        pstmt2.setDouble(1, auction.getCurrentPrice());
                        pstmt2.setString(2, auction.getId());
                        pstmt2.executeUpdate();
                    }

                    conn.commit();
                    System.out.println("[System]: Bid recorded for \"" + ANSI_YELLOW + currentUser.getName() + ANSI_RESET + "\" on auction \"" + ANSI_YELLOW + auction.getId() + ANSI_RESET + "\"");
                    return true;

                } catch (SQLException e) {
                    conn.rollback();
                    System.err.println("[Error]: Database Transaction Error: " + ANSI_RED + e.getMessage() + ANSI_RESET);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public boolean setupAutoBid(User currentUser, Auction auction, double maxBid, double increment) {
        if (auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("[Security]: You cannot set auto-bid on your own auction");
            return false;
        }

        Bidder bidder = new Bidder(currentUser);
        boolean isSuccess = auction.registerAutoBid(bidder, maxBid, increment);

        if (isSuccess) {
            String sql = "INSERT OR REPLACE INTO auto_bids (id, auction_id, bidder_id, max_bid, increment_amount, is_active) VALUES (?, ?, ?, ?, ?, 1)";

            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, "AB-" + System.currentTimeMillis());
                pstmt.setString(2, auction.getId());
                pstmt.setString(3, bidder.getId());
                pstmt.setDouble(4, maxBid);
                pstmt.setDouble(5, increment);

                pstmt.executeUpdate();
                System.out.println("[System]: Auto-Bid configuration saved for \"" + ANSI_YELLOW + currentUser.getName() + ANSI_RESET + "\"");
                return true;

            } catch (SQLException e) {
                System.err.println("[Error]: Database Error saving AutoBid: " + ANSI_RED + e.getMessage() + ANSI_RESET);
            }
        }
        return false;
    }
}