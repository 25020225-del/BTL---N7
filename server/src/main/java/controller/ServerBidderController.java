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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static utils.ConsoleColors.*;

/**
 * Controller responsible for handling bidding operations on the server side.
 * Manages manual bids, auto-bids, and ensures financial transactions
 * are executed securely, atomically, and asynchronously.
 */
public class ServerBidderController {

    /**
     * Processes a bid placed by a user asynchronously to prevent blocking the WebSocket NIO threads.
     * This method encapsulates the entire bidding transaction, ensuring atomic wallet
     * deductions and returning a Future for callback execution.
     *
     * @param currentUser The user attempting to place the bid.
     * @param auction     The target auction for the bid.
     * @param newMaxBid   The maximum amount the user is willing to bid.
     * @param isBot       Indicates whether this bid was triggered by the automated AutoBidEngine.
     * @return A CompletableFuture representing the asynchronous execution of the bid.
     */
    public CompletableFuture<Boolean> placeBidOnAuction(User currentUser, Auction auction, double newMaxBid, boolean isBot) {

        // Prevent the seller from bidding on their own auction to avoid price manipulation
        if (auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("[System]: " + RED + "You cannot bid on your own auction" + RESET);
            return CompletableFuture.completedFuture(false);
        }

        final Bidder bidder = new Bidder(currentUser);

        // Encapsulate the complex business and database logic into a Callable task
        Callable<Boolean> bidTask = () -> {
            // Snapshot the previous winner's state for refund processing
            Bidder previousWinner = auction.getWinningBidder();
            double amountToRefund = auction.getHighestMaxBid();

            try (Connection conn = DatabaseManager.getConnection()) {
                // Initialize a database transaction to ensure ACID properties
                conn.setAutoCommit(false);

                try {
                    // --- STEP 1: ATOMIC WALLET DEDUCTION (ANTI RACE-CONDITION) ---
                    // Combine SELECT and UPDATE into a single atomic query.
                    // This guarantees the balance never drops below 0 even under heavy concurrency.
                    String deductWalletSql = "UPDATE wallets SET balance = balance - ? WHERE user_id = ? AND balance >= ?";

                    try (PreparedStatement pstmt = conn.prepareStatement(deductWalletSql)) {
                        pstmt.setDouble(1, newMaxBid);       // Amount to deduct
                        pstmt.setString(2, bidder.getId());  // Target user
                        pstmt.setDouble(3, newMaxBid);       // Condition: Ensure balance is at least the bid amount

                        int rowsAffected = pstmt.executeUpdate();

                        // If rowsAffected is 0, the user lacks sufficient funds or the record does not exist.
                        if (rowsAffected == 0) {
                            System.out.println("[System]: \"" + YELLOW + currentUser.getName() + RESET + "\" has insufficient balance");
                            conn.rollback();
                            return false;
                        }
                    }

                    // --- STEP 2: PROCESSING AUCTION LOGIC IN RAM ---
                    // Now that funds are securely locked/deducted in the DB, proceed with auction validation.
                    boolean isSuccess = auction.placeBid(bidder, newMaxBid);

                    if (isSuccess) {
                        String now = LocalDateTime.now().toString();

                        // --- STEP 3: RECORD TRANSACTIONS & REFUND PREVIOUS WINNER ---
                        String insertTxnSql = "INSERT INTO wallet_transactions (id, user_id, amount, description, created_at) VALUES (?, ?, ?, ?, ?)";

                        // A. Log the deduction transaction for the new bidder
                        try (PreparedStatement pstmt = conn.prepareStatement(insertTxnSql)) {
                            pstmt.setString(1, "W-OUT-" + System.currentTimeMillis());
                            pstmt.setString(2, bidder.getId());
                            pstmt.setDouble(3, -newMaxBid);
                            pstmt.setString(4, "Auction bid placed for session: " + auction.getId());
                            pstmt.setString(5, now);
                            pstmt.executeUpdate();
                        }

                        // B. Refund the previous winner (if applicable and if they are not outbidding themselves)
                        if (previousWinner != null && !previousWinner.getId().equals(bidder.getId())) {
                            String refundSql = "UPDATE wallets SET balance = balance + ? WHERE user_id = ?";

                            // Return the locked funds to the previous winner's wallet
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

                        // C. Persist the Bid History (Bid Transaction)
                        String bidLogSql = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, bid_time) VALUES (?, ?, ?, ?, ?)";
                        try (PreparedStatement pstmt = conn.prepareStatement(bidLogSql)) {
                            pstmt.setString(1, "BID-" + System.currentTimeMillis());
                            pstmt.setString(2, auction.getId());
                            pstmt.setString(3, bidder.getId());
                            pstmt.setDouble(4, auction.getCurrentPrice());
                            pstmt.setString(5, now);
                            pstmt.executeUpdate();
                        }

                        // D. Update the current active price of the auction in the database
                        String updateAuctionSql = "UPDATE auctions SET current_price = ? WHERE id = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(updateAuctionSql)) {
                            pstmt.setDouble(1, auction.getCurrentPrice());
                            pstmt.setString(2, auction.getId());
                            pstmt.executeUpdate();
                        }

                        // Commit all database changes as a single unit of work
                        conn.commit();
                        System.out.println("[System]: Successfully placed bid for \"" + YELLOW + currentUser.getName() + RESET + "\"");
                        return true;

                    } else {
                        // RAM logic failed (e.g., bid is too low, or auction has ended).
                        // Rollback the DB transaction to revert the wallet deduction and leave the DB untouched.
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

        // PERFORMANCE FIX: Submit the task to the queue manager and return the CompletableFuture directly.
        // use .thenApply() to handle the result asynchronously once the DB worker finishes,
        // rather than using .get() which would block the current WebSocket network thread.
        return TransactionManager.submitTask(bidTask).thenApply(finalResult -> {
            // If the transaction was successful, notify connected clients and trigger bots
            if (finalResult) {
                Map<String, Object> updateData = new HashMap<>();
                updateData.put("auctionId", auction.getId());
                updateData.put("newPrice", auction.getCurrentPrice());
                updateData.put("winnerName", bidder.getUserName());

                server.ServerExtension.ClientManager.broadcast("UPDATE_AUCTION_PRICE", updateData, null);

                // Only trigger the AutoBidEngine if this was a manual user action to prevent infinite bot loops
                if (!isBot) {
                    AutoBidEngine.triggerBotScan(auction);
                }
            }
            return finalResult;
        }).exceptionally(ex -> {
            System.out.println("[System]: The transaction could not be executed via the queue: " + RED + ex.getMessage() + RESET);
            return false;
        });
    }

    /**
     * Registers an auto-bid configuration for a user on a specific auction.
     * The system will automatically place bids on behalf of the user up to the specified maximum amount.
     *
     * @param currentUser The user setting up the auto-bid.
     * @param auction     The target auction.
     * @param maxBid      The absolute maximum amount the user is willing to pay.
     * @param increment   The incremental amount to increase the bid by when outbidding competitors.
     * @return {@code true} if the auto-bid configuration was successfully verified and saved; {@code false} otherwise.
     */
    public boolean setupAutoBid(User currentUser, Auction auction, double maxBid, double increment) {

        // Prevent sellers from setting auto-bids on their own items
        if (auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("[System]: " + RED + "You cannot set an auto-bid on your own auction" + RESET);
            return false;
        }

        final Bidder bidder = new Bidder(currentUser);

        // Verify logical constraints in RAM (e.g., maxBid > currentPrice)
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
                    System.out.println("[Database]: Failed to save auto-bid config: " + RED + e.getMessage() + RESET);
                    return false;
                }
            };

            try {
                // Execute the database insertion task
                boolean saved = TransactionManager.submitTask(saveAutoBidTask).get();
                if (saved) {
                    System.out.println("[System]: Auto-Bid Configuration for \"" + YELLOW + currentUser.getName() + RESET + "\" has been saved.");

                    // Trigger the bot scan to process the newly added configuration immediately
                    AutoBidEngine.triggerBotScan(auction);
                    return true;
                }
            } catch (Exception e) {
                System.out.println("[System]: Execution error while saving auto-bid: " + RED + e.getMessage() + RESET);
            }
        }
        return false;
    }
}