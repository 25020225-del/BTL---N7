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

    private final BidDAO bidDAO;

    /**
     * Constructs the controller with the necessary Data Access Objects.
     * This implementation follows the Dependency Injection pattern to facilitate 
     * easier testing and decoupling.
     *
     * @param bidDAO The DAO responsible for bid-related database transactions.
     */
    public ServerBidderController(BidDAO bidDAO) {
        this.bidDAO = bidDAO;
    }

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

        // 1. Wrap the entire process (Validation -> DB -> RAM) into the Callable task
        // We move the synchronized block INSIDE the task to ensure atomicity without blocking the submission phase.
        Callable<Boolean> bidTask = () -> {
            // 2. Acquire the Striped Lock inside the worker thread.
            // This ensures that only one thread can process a bid for this specific auction at a time.
            synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                
                // 3. Perform REAL-TIME validation checks while holding the lock
                final User previousWinner = auction.getWinningBidder();
                final double previousHighestMaxBid = auction.getHighestMaxBid();

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

                // 4. Mathematical calculation for the NEW state
                User newWinner = previousWinner;
                double newHighestMaxBid = previousHighestMaxBid;
                double newCurrentPrice = auction.getCurrentPrice();
                LocalDateTime newEndTime = auction.getEndTime();

                if (previousWinner == null) {
                    newCurrentPrice = auction.getItem().getStartingPrice();
                    newHighestMaxBid = newMaxBid;
                    newWinner = currentUser;
                } else if (currentUser.getId().equals(previousWinner.getId())) {
                    if (newMaxBid > previousHighestMaxBid) {
                        newHighestMaxBid = newMaxBid;
                    }
                } else {
                    if (newMaxBid > previousHighestMaxBid) {
                        newCurrentPrice = previousHighestMaxBid + auction.getBidIncrement();
                        if (newCurrentPrice > newMaxBid) newCurrentPrice = newMaxBid;
                        newHighestMaxBid = newMaxBid;
                        newWinner = currentUser;
                    } else {
                        newCurrentPrice = newMaxBid + auction.getBidIncrement();
                        if (newCurrentPrice > previousHighestMaxBid) newCurrentPrice = previousHighestMaxBid;
                    }
                }

                // Anti-sniping calculation
                if (LocalDateTime.now().plusMinutes(1).isAfter(newEndTime)) {
                    LocalDateTime proposedEndTime = newEndTime.plusMinutes(2);
                    if (proposedEndTime.isBefore(auction.getMaxEndTime())) {
                        newEndTime = proposedEndTime;
                    } else {
                        newEndTime = auction.getMaxEndTime();
                    }
                }

                final LocalDateTime finalNewEndTime = newEndTime;
                final User finalNewWinner = newWinner;
                final double finalNewHighestMaxBid = newHighestMaxBid;
                final double finalNewCurrentPrice = newCurrentPrice;

                // 5. Database Interaction
                try (Connection conn = DatabaseManager.getConnection()) {
                    conn.setAutoCommit(false);

                    try {
                        boolean isDbSuccess = bidDAO.executeBidTransaction(
                                conn, 
                                currentUser, 
                                newMaxBid, 
                                previousWinner, 
                                previousHighestMaxBid, 
                                finalNewWinner,
                                finalNewHighestMaxBid,
                                finalNewCurrentPrice, 
                                auction.getId(), 
                                finalNewEndTime
                        );

                        if (isDbSuccess) {
                            conn.commit();
                            
                            // 6. Update RAM IMMEDIATELY while still holding the lock.
                            // This guarantees that the next thread to acquire the lock will see the updated state.
                            auction.placeBid(currentUser, newMaxBid);
                            
                            System.out.println("[System]: Successfully placed bid for \"" + YELLOW + currentUser.getName() + RESET + "\"");
                            return true;
                        } else {
                            System.out.println("[System]: \"" + YELLOW + currentUser.getName() + RESET + "\" has insufficient balance or transaction failed");
                            conn.rollback();
                            return false;
                        }
                    } catch (SQLException e) {
                        conn.rollback();
                        System.out.println("[Database]: Database TransactionError: " + RED + e.getMessage() + RESET);
                        return false;
                    }
                } catch (SQLException e) {
                    System.out.println("[Database]: Connection Error: " + RED + e.getMessage() + RESET);
                    return false;
                }
            }
        };

        // Submit the task to TransactionManager and chain the broadcast logic
        return TransactionManager.submitTask(bidTask).thenApply(finalResult -> {
            if (finalResult) {
                Map<String, Object> updateData = new HashMap<>();
                updateData.put("auctionId", auction.getId());
                updateData.put("newPrice", auction.getCurrentPrice());
                updateData.put("winnerName", currentUser.getUserName());

                server.ServerExtension.ClientManager.broadcast("UPDATE_AUCTION_PRICE", updateData, null);

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

        // Task: Save to DB FIRST, then update RAM if success
        Callable<Boolean> saveAutoBidTask = () -> {
            // Apply Striped Locking INSIDE the task to ensure atomicity
            synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                try {
                    // 1. Save to DB
                    boolean saved = bidDAO.saveAutoBid(currentUser, auction, maxBid, increment);
                    
                    if (saved) {
                        // 2. If DB success, update RAM
                        boolean ramSuccess = auction.registerAutoBid(currentUser, maxBid, increment);
                        if (ramSuccess) {
                            System.out.println("[System]: Auto-Bid Configuration for \"" + YELLOW + currentUser.getName() + RESET + "\" has been saved and registered.");
                            return true;
                        }
                    }
                    return false;
                } catch (SQLException e) {
                    System.out.println("[Database]: Failed to save auto-bid config: " + RED + e.getMessage() + RESET);
                    return false;
                }
            }
        };

        return TransactionManager.submitTask(saveAutoBidTask).thenApply(success -> {
            if (success) {
                // Immediately trigger a scan to see if the new bot should place a bid
                AutoBidEngine.triggerBotScan(auction);
            }
            return success;
        }).exceptionally(ex -> {
            System.out.println("[System]: Execution error while saving auto-bid: " + RED + ex.getMessage() + RESET);
            return false;
        });
    }
}