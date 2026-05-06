package controller;

import database.DatabaseManager;
import database.TransactionManager;
import database.dao.BidDAO;
import model.auction.Auction;
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

        double expectedPrice;
        double expectedMaxBid;
        String expectedWinnerId;
        synchronized (server.ServerExtension.AuctionManager.getLockForAuction(auction.getId())) {
            expectedPrice = auction.getCurrentPrice();
            expectedMaxBid = auction.getHighestMaxBid();
            expectedWinnerId = auction.getWinningBidder() != null ? auction.getWinningBidder().getId() : null;
        }

        // 1. Wrap the entire process into a Callable task
        Callable<Boolean> bidTask = () -> {
            // DB is the single source of truth. Do NOT lock RAM while doing DB I/O.
            BidDAO.BidCommitResult commitResult;
            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    commitResult = bidDAO.executeBidTransactionSourceOfTruth(
                            conn,
                            auction.getId(),
                            currentUser,
                            newMaxBid,
                            expectedPrice,
                            expectedMaxBid,
                            expectedWinnerId
                    );

                    if (commitResult != null) {
                        conn.commit();
                    } else {
                        conn.rollback();
                        System.out.println("[System]: \"" + YELLOW + currentUser.getName() + RESET + "\" transaction failed (insufficient balance or state changed)");
                        return false;
                    }
                } catch (SQLException e) {
                    conn.rollback();
                    System.out.println("[Database]: Transaction Error: " + RED + e.getMessage() + RESET);
                    return false;
                }
            } catch (SQLException e) {
                System.out.println("[Database]: Connection Error: " + RED + e.getMessage() + RESET);
                return false;
            }

            // RAM Update Phase: exactly ONE short synchronized block AFTER commit.
            synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                // Guard: avoid overwriting a newer in-RAM state (another bid may have committed & synced first).
                if (auction.getCurrentPrice() <= commitResult.newCurrentPrice) {
                    User winner = null;
                    if (commitResult.newWinnerId != null) {
                        winner = new User();
                        winner.setId(commitResult.newWinnerId);
                    }
                    Auction.BidResult ramResult = new Auction.BidResult(
                            winner,
                            commitResult.newHighestMaxBid,
                            commitResult.newCurrentPrice,
                            commitResult.newEndTime
                    );
                    auction.applyBidResult(currentUser, ramResult);
                }
            }

            System.out.println("[System]: Successfully placed bid for \"" + YELLOW + currentUser.getName() + RESET + "\"");
            return true;
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
            } else {
                // Task failed - could be Optimistic Locking conflict
                if (!isBot) {
                    // Send a direct error message to the specific client that placed the bid
                    server.ServerExtension.ClientManager.sendToUser(
                            currentUser.getId(), 
                            "GENERAL_ERROR", 
                            "Giá sản phẩm đã thay đổi bởi người dùng khác. Vui lòng cập nhật và thử lại!"
                    );
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