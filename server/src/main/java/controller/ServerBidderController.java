package controller;

import database.DatabaseManager;
import database.TransactionManager;
import database.dao.BidDAO;
import model.auction.Auction;
import model.finance.BidTransaction;
import model.user.User;
import server.ServerExtension.AuctionManager;
import service.AutoBidEngine;

import java.sql.Connection;
import java.sql.SQLException;
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

    private final BidDAO bidDAO = new BidDAO();

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

        final User previousWinner;
        final double previousHighestMaxBid;
        final BidTransaction newTransaction;

        // 1. Optmistic Locking Approach:
        // Lock the auction just long enough to validate and update the RAM state.
        // This avoids holding the lock during slow Database I/O, removing the bottleneck.
        synchronized (AuctionManager.getLockForAuction(auction.getId())) {
            previousWinner = auction.getWinningBidder();
            previousHighestMaxBid = auction.getHighestMaxBid();
            
            // Perform validation and update RAM immediately
            newTransaction = auction.placeBid(currentUser, newMaxBid);
            
            if (newTransaction == null) {
                return CompletableFuture.completedFuture(false); // Validation failed
            }
        }

        // 2. Encapsulate DB I/O into a Callable to be processed asynchronously by the Worker thread.
        Callable<Boolean> bidTask = () -> {
            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false); // Begin ACID transaction

                try {
                    // executeBidTransaction handles the complex deduction/refund logic
                    boolean isDbSuccess = bidDAO.executeBidTransaction(conn, currentUser, newMaxBid, previousWinner, previousHighestMaxBid, newTransaction.getBidAmount(), auction.getId());

                    if (isDbSuccess) {
                        conn.commit(); // Finalize all changes
                        System.out.println("[System]: Successfully placed bid for \"" + YELLOW + currentUser.getName() + RESET + "\"");
                        return true;
                    } else {
                        System.out.println("[System]: \"" + YELLOW + currentUser.getName() + RESET + "\" has insufficient balance");
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

        // 3. Submit the task to TransactionManager.
        return TransactionManager.submitTask(bidTask).thenApply(finalResult -> {
            if (finalResult) {
                // Success: Broadcast updates
                Map<String, Object> updateData = new HashMap<>();
                updateData.put("auctionId", auction.getId());
                updateData.put("newPrice", auction.getCurrentPrice());
                updateData.put("winnerName", currentUser.getUserName());

                server.ServerExtension.ClientManager.broadcast("UPDATE_AUCTION_PRICE", updateData, null);

                if (!isBot) {
                    AutoBidEngine.triggerBotScan(auction);
                }
            } else {
                // Failure: Rollback RAM synchronously
                synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                    auction.revertLastBid(previousWinner, previousHighestMaxBid, newTransaction);
                }
            }
            return finalResult;
        }).exceptionally(ex -> {
            System.out.println("[System]: The transaction could not be executed via the queue: " + RED + ex.getMessage() + RESET);
            // Exception: Rollback RAM synchronously
            synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                auction.revertLastBid(previousWinner, previousHighestMaxBid, newTransaction);
            }
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

        // Apply Striped Locking here as well to ensure atomic bot registration relative to bid processing
        synchronized (AuctionManager.getLockForAuction(auction.getId())) {
            // Register bot in RAM first
            boolean isSuccess = auction.registerAutoBid(currentUser, maxBid, increment);

            if (isSuccess) {
                Callable<Boolean> saveAutoBidTask = () -> {
                    try {
                        return bidDAO.saveAutoBid(currentUser, auction, maxBid, increment);
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
}