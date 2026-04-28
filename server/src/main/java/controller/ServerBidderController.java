package controller;

import database.DatabaseManager;
import database.TransactionManager;
import model.Auction;
import model.Bidder;
import model.User;
import service.AutoBidEngine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;

import static utils.ConsoleColors.*;

public class ServerBidderController {

    /**
     * Bid processing (Manual or Auto-bid) uses the Transaction Queue.
     * @param isBot true if the command is triggered by an automated bot.
     */
    public boolean placeBidOnAuction(User currentUser, Auction auction, double newMaxBid, boolean isBot) {

        // 1. Perform basic security checks before adding to the queue
        if (auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("[System]: " + RED + "You cannot bid on your own auction" + RESET);
            return false;
        }

        final Bidder bidder = new Bidder(currentUser);

        // 2. Encapsulating business logic into a Callable task
        Callable<Boolean> bidTask = () -> {
            // Save the information of the previous winner for a refund
            Bidder previousWinner = auction.getWinningBidder();
            double amountToRefund = auction.getHighestMaxBid();

            try (Connection conn = DatabaseManager.getConnection()) {
                // Start a Database Transaction
                conn.setAutoCommit(false);

                try {
                    // --- STEP 1: CHECK THE WALLET BALANCE FROM THE DATABASE ---
                    double currentBalance = 0;
                    String checkWalletSql = "SELECT balance FROM wallets WHERE user_id = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(checkWalletSql)) {
                        pstmt.setString(1, bidder.getId());
                        ResultSet rs = pstmt.executeQuery();
                        if (rs.next()) {
                            currentBalance = rs.getDouble("balance");
                        } else {
                            System.out.println("[System]: Balance not found for: " + YELLOW + bidder.getId() + RESET);
                            conn.rollback();
                            return false;
                        }
                    }

                    if (currentBalance < newMaxBid) {
                        System.out.println("[System]: \"" + YELLOW + currentUser.getName() + RESET + "\" has insufficient balance");
                        conn.rollback();
                        return false;
                    }

                    // --- STEP 2: PROCESSING AUCTION LOGIC IN RAM ---
                    // This function performs a comparison of the current bid
                    boolean isSuccess = auction.placeBid(bidder, newMaxBid);

                    if (isSuccess) {
                        String now = LocalDateTime.now().toString();

                        // --- STEP 3: UPDATE FINANCIAL DATA (UPDATE DB) ---
                        String updateWalletSql = "UPDATE wallets SET balance = balance + ? WHERE user_id = ?";
                        String insertTxnSql = "INSERT INTO wallet_transactions (id, user_id, amount, description, created_at) VALUES (?, ?, ?, ?, ?)";

                        // A. Deduct from new bidder's deposit
                        try (PreparedStatement pstmt = conn.prepareStatement(updateWalletSql)) {
                            pstmt.setDouble(1, -newMaxBid);
                            pstmt.setString(2, bidder.getId());
                            pstmt.executeUpdate();
                        }
                        try (PreparedStatement pstmt = conn.prepareStatement(insertTxnSql)) {
                            pstmt.setString(1, "W-OUT-" + System.currentTimeMillis());
                            pstmt.setString(2, bidder.getId());
                            pstmt.setDouble(3, -newMaxBid);
                            pstmt.setString(4, "Set the price for the session: " + auction.getId());
                            pstmt.setString(5, now);
                            pstmt.executeUpdate();
                        }

                        // B. Refund to the previous winner (if applicable and not themselves)
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
                                pstmt.setString(4, "Refund for price overrun during the session: " + auction.getId());
                                pstmt.setString(5, now);
                                pstmt.executeUpdate();
                            }
                        }

                        // C. Save Bid History (Bid Transaction)
                        String bidLogSql = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, bid_time) VALUES (?, ?, ?, ?, ?)";
                        try (PreparedStatement pstmt = conn.prepareStatement(bidLogSql)) {
                            pstmt.setString(1, "BID-" + System.currentTimeMillis());
                            pstmt.setString(2, auction.getId());
                            pstmt.setString(3, bidder.getId());
                            pstmt.setDouble(4, auction.getCurrentPrice());
                            pstmt.setString(5, now);
                            pstmt.executeUpdate();
                        }

                        // D. Current price update for the auction
                        String updateAuctionSql = "UPDATE auctions SET current_price = ? WHERE id = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(updateAuctionSql)) {
                            pstmt.setDouble(1, auction.getCurrentPrice());
                            pstmt.setString(2, auction.getId());
                            pstmt.executeUpdate();
                        }

                        // End of the financial trading cycle
                        conn.commit();
                        System.out.println("[System]: Successfully bid for \"" + YELLOW + currentUser.getName() + RESET + "\"");
                        return true;

                    } else {
                        conn.rollback();
                        return false;
                    }

                } catch (SQLException e) {
                    conn.rollback();
                    System.out.println("[Database]: Database Transaction Error: " + RED + e.getMessage() + RESET);
                    return false;
                }
            } catch (SQLException e) {
                System.out.println("[Database]: Connection Error: " + RED + e.getMessage() + RESET);
                return false;
            }
        };

        // 3. Submit the task to a single queue and wait for the result (Blocking)
        try {
            // .get() will pause the current Socket thread to wait for the Worker Thread to finish processing the task
            boolean finalResult = TransactionManager.submitTask(bidTask).get();

            // 4. If successful and this is a real user, enable the Engine for the response bot
            if (finalResult && !isBot) {
                AutoBidEngine.triggerBotScan(auction);
            }
            return finalResult;

        } catch (Exception e) {
            System.out.println("[System]: The order cannot be executed via the order queue: " + RED + e.getMessage() + RESET);
            return false;
        }
    }

    /**
     * Set up Auto-bid (Automatic Bidding)
     */
    public boolean setupAutoBid(User currentUser, Auction auction, double maxBid, double increment) {
        if (auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("[System]: " + RED + "You cannot set auto-bid on your own auction" + RESET);
            return false;
        }

        final Bidder bidder = new Bidder(currentUser);

        // Logic in-memory check
        boolean isSuccess = auction.registerAutoBid(bidder, maxBid, increment);

        if (isSuccess) {
            Callable<Boolean> saveAutoBidTask = () -> {
                String sql = "INSERT OR REPLACE INTO auto_bids (id, auction_id, bidder_id, max_bid, increment_amount, is_active) VALUES (?, ?, ?, ?, ?, 1)";
                try (Connection conn = DatabaseManager.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, "AB-" + System.currentTimeMillis());
                    pstmt.setString(2, auction.getId());
                    pstmt.setString(3, bidder.getId());
                    pstmt.setDouble(4, maxBid);
                    pstmt.setDouble(5, increment);
                    pstmt.executeUpdate();
                    return true;
                } catch (SQLException e) {
                    return false;
                }
            };

            try {
                boolean saved = TransactionManager.submitTask(saveAutoBidTask).get();
                if (saved) {
                    System.out.println("[System]: Auto-Bid Setting for \"" + YELLOW + currentUser.getName() + RESET + "\" has been saved.");
                    AutoBidEngine.triggerBotScan(auction);
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}