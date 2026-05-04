package controller;

import database.DatabaseManager;
import database.TransactionManager;
import model.auction.Auction;
import model.user.User;
import service.AutoBidEngine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static utils.ConsoleColors.*;

/**
 * Controller responsible for handling bidding operations on the server side.
 * It manages manual bid placements, automated bidding configurations,
 * and ensures financial transactions (deductions and refunds) are executed
 * atomically and asynchronously.
 */
public class ServerBidderController {

    /**
     * Processes a bid placement attempt for a specific auction.
     * This method executes a complex database transaction that includes:
     * <ul>
     *     <li>Atomic wallet balance deduction.</li>
     *     <li>Auction state validation (RAM).</li>
     *     <li>Refunding the previous leading bidder's max bid.</li>
     *     <li>Persisting bid and wallet transaction logs.</li>
     * </ul>
     *
     * @param currentUser The user attempting to place the bid.
     * @param auction     The target auction session.
     * @param newMaxBid   The maximum amount the user is offering.
     * @param isBot       Indicates if the bid was placed by the {@link AutoBidEngine}.
     * @return A {@link CompletableFuture} that resolves to {@code true} if the bid
     * was successfully placed; {@code false} otherwise.
     */
    public CompletableFuture<Boolean> placeBidOnAuction(User currentUser, Auction auction, double newMaxBid, boolean isBot) {

        // Prevent users from bidding on items they are selling
        if (auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("[System]: " + RED + "You cannot bid on your own auction" + RESET);
            return CompletableFuture.completedFuture(false);
        }

        // Encapsulate the transaction logic into a task for the database worker thread
        Callable<Boolean> bidTask = () -> {
            User previousWinner = auction.getWinningBidder();
            double amountToRefund = auction.getHighestMaxBid();

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false); // Begin ACID transaction

                try {
                    // STEP 1: Atomic wallet deduction with balance check
                    String deductWalletSql = "UPDATE wallets SET balance = balance - ? WHERE user_id = ? AND balance >= ?";

                    try (PreparedStatement pstmt = conn.prepareStatement(deductWalletSql)) {
                        pstmt.setDouble(1, newMaxBid);
                        pstmt.setString(2, currentUser.getId());
                        pstmt.setDouble(3, newMaxBid);

                        int rowsAffected = pstmt.executeUpdate();

                        if (rowsAffected == 0) {
                            System.out.println("[System]: \"" + YELLOW + currentUser.getName() + RESET + "\" has insufficient balance");
                            conn.rollback();
                            return false;
                        }
                    }

                    // STEP 2: Validate auction logic in RAM
                    boolean isSuccess = auction.placeBid(currentUser, newMaxBid);

                    if (isSuccess) {
                        String now = LocalDateTime.now().toString();
                        String insertTxnSql = "INSERT INTO wallet_transactions (id, user_id, amount, description, created_at) VALUES (?, ?, ?, ?, ?)";

                        // Log the withdrawal transaction
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

                        // STEP 4: Record the bid history and update auction current price
                        String bidLogSql = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, bid_time) VALUES (?, ?, ?, ?, ?)";
                        try (PreparedStatement pstmt = conn.prepareStatement(bidLogSql)) {
                            pstmt.setString(1, "BID-" + System.currentTimeMillis());
                            pstmt.setString(2, auction.getId());
                            pstmt.setString(3, currentUser.getId());
                            pstmt.setDouble(4, auction.getCurrentPrice());
                            pstmt.setString(5, now);
                            pstmt.executeUpdate();
                        }

                        String updateAuctionSql = "UPDATE auctions SET current_price = ? WHERE id = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(updateAuctionSql)) {
                            pstmt.setDouble(1, auction.getCurrentPrice());
                            pstmt.setString(2, auction.getId());
                            pstmt.executeUpdate();
                        }

                        conn.commit(); // Finalize all changes
                        System.out.println("[System]: Successfully placed bid for \"" + YELLOW + currentUser.getName() + RESET + "\"");
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

        // Submit task to TransactionManager to avoid blocking the NIO network thread
        return TransactionManager.submitTask(bidTask).thenApply(finalResult -> {
            if (finalResult) {
                // Broadcast price update to all connected clients
                Map<String, Object> updateData = new HashMap<>();
                updateData.put("auctionId", auction.getId());
                updateData.put("newPrice", auction.getCurrentPrice());
                updateData.put("winnerName", currentUser.getUserName());

                server.ServerExtension.ClientManager.broadcast("UPDATE_AUCTION_PRICE", updateData, null);

                // Trigger the auto-bid engine scan if this was a manual bid
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
     * Registers an automated bidding configuration (bot) for a user asynchronously.
     *
     * @param currentUser The user setting up the bot.
     * @param auction     The target auction session.
     * @param maxBid      The maximum budget the user is willing to spend.
     * @param increment   The minimum step to increase the price when outbidding others.
     * @return A {@link CompletableFuture} resolving to true if configured successfully.
     */
    public CompletableFuture<Boolean> setupAutoBid(User currentUser, Auction auction, double maxBid, double increment) {

        if (auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("[System]: " + RED + "You cannot set an auto-bid on your own auction" + RESET);
            return CompletableFuture.completedFuture(false);
        }

        // Register bot in RAM first
        boolean isSuccess = auction.registerAutoBid(currentUser, maxBid, increment);

        if (isSuccess) {
            Callable<Boolean> saveAutoBidTask = () -> {
                String sql = "INSERT OR REPLACE INTO auto_bids (id, auction_id, bidder_id, max_bid, increment_amount, is_active) VALUES (?, ?, ?, ?, ?, 1)";

                try (Connection conn = DatabaseManager.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(sql)) {

                    pstmt.setString(1, "AB-" + System.currentTimeMillis());
                    pstmt.setString(2, auction.getId());
                    pstmt.setString(3, currentUser.getId());
                    pstmt.setDouble(4, maxBid);
                    pstmt.setDouble(5, increment);

                    pstmt.executeUpdate();
                    return true;
                } catch (SQLException e) {
                    System.out.println("[Database]: Failed to save auto-bid config: " + RED + e.getMessage() + RESET);
                    return false;
                }
            };

            return TransactionManager.submitTask(saveAutoBidTask).thenApply(saved -> {
                if (saved) {
                    System.out.println("[System]: Auto-Bid Configuration for \"" + YELLOW + currentUser.getName() + RESET + "\" has been saved.");
                    // Immediately trigger a scan to see if the new bot should place a bid
                    AutoBidEngine.triggerBotScan(auction);
                }
                return saved;
            }).exceptionally(ex -> {
                System.out.println("[System]: Execution error while saving auto-bid: " + RED + ex.getMessage() + RESET);
                return false;
            });
        }
        return CompletableFuture.completedFuture(false);
    }
}