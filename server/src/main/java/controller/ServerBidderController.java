package controller;

import database.DatabaseManager;
import model.Auction;
import model.Bidder;
import model.User;
import service.AutoBidEngine;
import server.ServerExtension.AuctionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static utils.ConsoleColors.*;

public class ServerBidderController {

    /**
     * Resolving manual/autobid
     * @param isBot true if the command is activated by a bot.
     */
    public boolean placeBidOnAuction(User currentUser, Auction auction, double newMaxBid, boolean isBot) {
        // seller mustn't bid on their own auction
        if (auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("[Security]: " + RED + "You cannot bid on your own auction" + RESET);
            return false;
        }

        Bidder bidder = new Bidder(currentUser);

        // Get Lock respective to auction ID to avoid Race Condition when many users bid at the same time
        Object auctionLock = AuctionManager.getLockForAuction(auction.getId());

        synchronized (auctionLock) {
            // Save old temp winner's info and bid amount for refund
            Bidder previousWinner = auction.getWinningBidder();
            double amountToRefund = auction.getHighestMaxBid();

            try (Connection conn = DatabaseManager.getConnection()) {
                // Start Database Transaction
                conn.setAutoCommit(false);

                try {
                    // Check wallet balance from database
                    double currentBalance = 0;
                    String checkWalletSql = "SELECT balance FROM wallets WHERE user_id = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(checkWalletSql)) {
                        pstmt.setString(1, bidder.getId());
                        ResultSet rs = pstmt.executeQuery();
                        if (rs.next()) {
                            currentBalance = rs.getDouble("balance");
                        } else {
                            System.out.println("[Error]: Wallet not found for user: " + YELLOW + bidder.getId() + RESET);
                            conn.rollback();
                            return false;
                        }
                    }

                    // Check afford ability
                    if (currentBalance < newMaxBid) {
                        System.out.println("[System]: " + YELLOW + currentUser.getName() + RESET + " does not have enough money");
                        conn.rollback();
                        return false;
                    }

                    // Executing bid matching logic in-memory (RAM)
                    boolean isSuccess = auction.placeBid(bidder, newMaxBid);

                    if (isSuccess) {
                        String now = LocalDateTime.now().toString();

                        // Save and store new winner's bid amount
                        String updateWalletSql = "UPDATE wallets SET balance = balance + ? WHERE user_id = ?";
                        String insertTxnSql = "INSERT INTO wallet_transactions (id, user_id, amount, description, created_at) VALUES (?, ?, ?, ?, ?)";

                        // Temp remove money
                        try (PreparedStatement pstmt = conn.prepareStatement(updateWalletSql)) {
                            pstmt.setDouble(1, -newMaxBid);
                            pstmt.setString(2, bidder.getId());
                            pstmt.executeUpdate();
                        }
                        try (PreparedStatement pstmt = conn.prepareStatement(insertTxnSql)) {
                            pstmt.setString(1, "W-OUT-" + System.currentTimeMillis());
                            pstmt.setString(2, bidder.getId());
                            pstmt.setDouble(3, -newMaxBid);
                            pstmt.setString(4, "Placed bid for auction: " + auction.getId());
                            pstmt.setString(5, now);
                            pstmt.executeUpdate();
                        }

                        // Refund for old winner
                        if (previousWinner != null && !previousWinner.getId().equals(bidder.getId())) {
                            try (PreparedStatement pstmt = conn.prepareStatement(updateWalletSql)) {
                                pstmt.setDouble(1, amountToRefund);
                                pstmt.setString(2, previousWinner.getId());
                                pstmt.executeUpdate();
                            }
                            try (PreparedStatement pstmt = conn.prepareStatement(insertTxnSql)) {
                                pstmt.setString(1, "W-REF-" + System.currentTimeMillis());
                                pstmt.setString(2, previousWinner.getId());
                                pstmt.setDouble(3, amountToRefund);
                                pstmt.setString(4, "Refund outbid amount for auction: " + auction.getId());
                                pstmt.setString(5, now);
                                pstmt.executeUpdate();
                            }
                        }

                        // new Bid Transaction info
                        String bidLogSql = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, bid_time) VALUES (?, ?, ?, ?, ?)";
                        try (PreparedStatement pstmt = conn.prepareStatement(bidLogSql)) {
                            pstmt.setString(1, "BID-" + System.currentTimeMillis());
                            pstmt.setString(2, auction.getId());
                            pstmt.setString(3, bidder.getId());
                            pstmt.setDouble(4, auction.getCurrentPrice());
                            pstmt.setString(5, now);
                            pstmt.executeUpdate();
                        }

                        // Update current amount
                        String updateAuctionSql = "UPDATE auctions SET current_price = ? WHERE id = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(updateAuctionSql)) {
                            pstmt.setDouble(1, auction.getCurrentPrice());
                            pstmt.setString(2, auction.getId());
                            pstmt.executeUpdate();
                        }

                        // Confirm completing financial cycle
                        conn.commit();
                        System.out.println("[System]: Bid recorded successfully for \"" + YELLOW + currentUser.getName() + RESET + "\"");

                        // if bidder = human => call bot to respond
                        if (!isBot) {
                            AutoBidEngine.triggerBotScan(auction);
                        }
                        return true;

                    } else {
                        // if logic auction declines
                        conn.rollback();
                        return false;
                    }

                } catch (SQLException e) {
                    conn.rollback();
                    System.out.println("[Error]: Database Transaction Error: " + RED + e.getMessage() + RESET);
                }
            } catch (SQLException e) {
                System.out.println("[Error]: Database Connection Error: " + RED + e.getMessage() + RESET);
            }
        }
        return false;
    }

    public boolean setupAutoBid(User currentUser, Auction auction, double maxBid, double increment) {
        if (auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("[Security]: " + RED + "You cannot set auto-bid on your own auction" + RESET);
            return false;
        }

        Bidder bidder = new Bidder(currentUser);
        boolean isSuccess = auction.registerAutoBid(bidder, maxBid, increment); //

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
                System.out.println("[System]: Auto-Bid configuration saved for \"" + YELLOW + currentUser.getName() + RESET + "\"");

                AutoBidEngine.triggerBotScan(auction);
                return true;

            } catch (SQLException e) {
                System.out.println("[Error]: Database Error saving AutoBid: " + RED + e.getMessage() + RESET);
            }
        }
        return false;
    }
}