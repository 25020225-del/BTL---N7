package controller;

import database.DatabaseManager;
import database.TransactionManager;
import database.dao.BidDAO;
import model.auction.Auction;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;
import service.AutoBidEngine;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Controller responsible for handling bidding operations on the server side.
 * It manages manual bid placements, automated bidding configurations,
 * and ensures financial transactions (deductions and refunds) are executed
 * atomically and asynchronously.
 */
public class ServerBidderController {

    private static final Logger log = LoggerFactory.getLogger(ServerBidderController.class);
    private static final int PLACE_BID_MAX_RETRIES = 3;

    private final BidDAO bidDAO;

    /**
     * Constructs the controller with the necessary Data Access Objects.
     *
     * @param bidDAO The DAO responsible for bid-related database transactions.
     */
    public ServerBidderController(BidDAO bidDAO) {
        this.bidDAO = bidDAO;
    }

    /**
     * Processes a bid placement attempt for a specific auction.
     *
     * @param currentUser The user attempting to place the bid.
     * @param auction     The target auction session.
     * @param newMaxBid   The maximum amount the user is offering.
     * @param isBot       Indicates if the bid was placed by the {@link AutoBidEngine}.
     * @return A {@link CompletableFuture} that resolves to {@code true} if the bid
     * was successfully placed; {@code false} otherwise.
     */
    public CompletableFuture<Boolean> placeBidOnAuction(User currentUser, Auction auction, double newMaxBid, boolean isBot) {

        if (auction.getSeller().getId().equals(currentUser.getId())) {
            log.warn("Cannot bid on own auction: {}", currentUser.getId());
            return CompletableFuture.completedFuture(false);
        }

        Callable<Boolean> bidTask = () -> {
            BidDAO.BidCommitResult commitResult = null;

            for (int attempt = 0; attempt < PLACE_BID_MAX_RETRIES; attempt++) {
                double expectedPrice;
                double expectedMaxBid;
                String expectedWinnerId;

                synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                    expectedPrice = auction.getCurrentPrice();
                    expectedMaxBid = auction.getHighestMaxBid();
                    expectedWinnerId = auction.getWinningBidder() != null
                            ? auction.getWinningBidder().getId()
                            : null;
                }

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
                                expectedWinnerId,
                                isBot
                        );

                        if (commitResult != null) {
                            conn.commit();
                            break;
                        }
                        conn.rollback();
                        log.debug("Bid attempt {} optimistic conflict for user {}", attempt + 1, currentUser.getName());
                    } catch (SQLException e) {
                        conn.rollback();
                        log.error("Transaction error placing bid", e);
                        return false;
                    }
                } catch (SQLException e) {
                    log.error("Connection error placing bid", e);
                    return false;
                }
            }

            if (commitResult == null) {
                log.warn("Bid failed after {} retries (optimistic lock / validation): {}", PLACE_BID_MAX_RETRIES, currentUser.getName());
                return false;
            }

            synchronized (AuctionManager.getLockForAuction(auction.getId())) {
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

            log.info("Successfully placed bid for user {}", currentUser.getName());
            return true;
        };

        return TransactionManager.submitTask(bidTask).thenApply(finalResult -> {
            if (finalResult) {
                Map<String, Object> updateData = new HashMap<>();
                updateData.put("auctionId", auction.getId());
                updateData.put("newPrice", auction.getCurrentPrice());
                updateData.put("winnerName", currentUser.getUserName());

                ClientManager.broadcast("UPDATE_AUCTION_PRICE", updateData, null);

                if (!isBot) {
                    AutoBidEngine.triggerBotScan(auction);
                }
            } else {
                if (!isBot) {
                    ClientManager.sendToUser(
                            currentUser.getId(),
                            "GENERAL_ERROR",
                            "Giá sản phẩm đã thay đổi bởi người dùng khác. Vui lòng cập nhật và thử lại!"
                    );
                }
            }
            return finalResult;
        }).exceptionally(ex -> {
            log.error("Transaction could not be executed via queue", ex);
            return false;
        });
    }

    /**
     * Registers an automated bidding configuration (bot) for a user asynchronously.
     */
    public CompletableFuture<Boolean> setupAutoBid(User currentUser, Auction auction, double maxBid, double increment) {

        if (auction.getSeller().getId().equals(currentUser.getId())) {
            log.warn("Cannot set auto-bid on own auction");
            return CompletableFuture.completedFuture(false);
        }

        Callable<Boolean> saveAutoBidTask = () -> {
            synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                try {
                    boolean saved = bidDAO.saveAutoBid(currentUser, auction, maxBid, increment);

                    if (saved) {
                        boolean ramSuccess = auction.registerAutoBid(currentUser, maxBid, increment);
                        if (ramSuccess) {
                            log.info("Auto-Bid Configuration saved for user {}", currentUser.getName());
                            return true;
                        }
                    }
                    return false;
                } catch (SQLException e) {
                    log.error("Failed to save auto-bid config", e);
                    return false;
                }
            }
        };

        return TransactionManager.submitTask(saveAutoBidTask).thenApply(success -> {
            if (success) {
                AutoBidEngine.triggerBotScan(auction);
            }
            return success;
        }).exceptionally(ex -> {
            log.error("Execution error while saving auto-bid", ex);
            return false;
        });
    }
}
