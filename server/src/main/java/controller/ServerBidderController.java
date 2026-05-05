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

        // Encapsulate the entire atomic operation (Lock -> DB -> RAM) into a Callable
        // to be processed asynchronously by the single-threaded TransactionManager.
        Callable<Boolean> bidTask = () -> {
            // This entire block is now executed on the single worker thread,
            // ensuring sequential processing without needing a lock at the controller level.
            // However, for absolute safety against future changes (e.g., multi-threaded worker),
            // the lock is acquired here, guaranteeing atomicity for this specific auction.
            synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                
                // 1. Get current state from RAM for validation and DB transaction.
                final User previousWinner = auction.getWinningBidder();
                final double previousHighestMaxBid = auction.getHighestMaxBid();

                // 2. Perform pre-validation checks that don't modify state
                if (auction.getStatus().equals(Auction.STATUS_DELETED)) {
                    System.out.println("[Error]: " + RED + "The auction session has been deleted by Admin" + RESET);
                    return false;
                }
                if (!auction.getStatus().equals(Auction.STATUS_RUNNING) || java.time.LocalDateTime.now().isAfter(auction.getEndTime())) {
                    System.out.println("[Error]: " + RED + "Cannot place a bid. The auction is not running or has already ended" + RESET);
                    return false;
                }
                double minRequiredBid = (previousWinner == null) ? auction.getCurrentPrice() : (auction.getCurrentPrice() + auction.getBidIncrement());
                if (newMaxBid < minRequiredBid) {
                    System.out.println("[Error]: " + RED + "Bid must be greater than or equal to VND " + minRequiredBid + RESET);
                    return false;
                }

                // 3. Calculate the new current price based on bidding logic
                double newCurrentPrice;
                if (previousWinner == null) {
                    newCurrentPrice = auction.getItem().getStartingPrice();
                } else if (currentUser.getId().equals(previousWinner.getId())) {
                    newCurrentPrice = auction.getCurrentPrice(); // Price doesn't change when outbidding self
                } else {
                    if (newMaxBid > previousHighestMaxBid) {
                        newCurrentPrice = previousHighestMaxBid + auction.getBidIncrement();
                        if (newCurrentPrice > newMaxBid) {
                            newCurrentPrice = newMaxBid;
                        }
                    } else {
                        newCurrentPrice = newMaxBid + auction.getBidIncrement();
                        if (newCurrentPrice > previousHighestMaxBid) {
                            newCurrentPrice = previousHighestMaxBid;
                        }
                    }
                }

                // 4. Execute DB transaction FIRST
                try (Connection conn = DatabaseManager.getConnection()) {
                    conn.setAutoCommit(false); // Begin ACID transaction

                    try {
                        boolean isDbSuccess = bidDAO.executeBidTransaction(conn, currentUser, newMaxBid, previousWinner, previousHighestMaxBid, newCurrentPrice, auction.getId());

                        if (isDbSuccess) {
                            conn.commit(); // Finalize all changes
                            
                            // 5. DB Success, now update RAM atomically INSIDE the lock
                            auction.placeBid(currentUser, newMaxBid);
                            
                            System.out.println("[System]: Successfully placed bid for \"" + YELLOW + currentUser.getName() + RESET + "\"");
                            return true;
                        } else {
                            System.out.println("[System]: \"" + YELLOW + currentUser.getName() + RESET + "\" has insufficient balance");
                            conn.rollback();
                            // NO need to revert RAM because we never modified it!
                            return false;
                        }

                    } catch (SQLException e) {
                        conn.rollback();
                        // NO need to revert RAM because we never modified it!
                        System.out.println("[Database]: Database TransactionError: " + RED + e.getMessage() + RESET);
                        return false;
                    }
                } catch (SQLException e) {
                    System.out.println("[Database]: Connection Error: " + RED + e.getMessage() + RESET);
                    // NO need to revert RAM because we never modified it!
                    return false;
                }
            } // Lock is released here, after both DB and RAM are synced.
        };

        // Submit the task to TransactionManager. The result is handled asynchronously.
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
            // No need for a failure case here, as RAM was never modified if DB failed.
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