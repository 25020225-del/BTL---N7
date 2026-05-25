package controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import database.DatabaseManager;
import database.TransactionManager;
import database.dao.BidDAO;
import model.auction.Auction;
import model.user.User;
import network.ErrorPayload;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;
import service.AutoBidEngine;
import service.AutoBidLockService;
import utils.JacksonConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Controller executing real-time bidding operations, transaction persisted states,
 * and dispatching asynchronous proxy configuration triggers to the automated bot engine.
 */
public class ServerBidderController {

    private static final Logger log = LoggerFactory.getLogger(ServerBidderController.class);
    static final String MSG_ACCOUNT_BLOCKED = "Tài khoản của bạn đã bị Quản trị viên khóa, không thể thực hiện thao tác này.";
    static final String ERR_CODE_BLOCKED = "ERR_BID_403";
    private final BidDAO bidDAO;

    public ServerBidderController(BidDAO bidDAO) {
        this.bidDAO = bidDAO;
    }

    public CompletableFuture<Boolean> placeManualBid(User currentUser, Auction auction, long newMaxBid) {
        return placeBidOnAuction(currentUser, auction, newMaxBid, false);
    }

    public CompletableFuture<Boolean> placeBidOnAuctionFromBot(User botUser, Auction auction, long newMaxBid) {
        return placeBidOnAuction(botUser, auction, newMaxBid, true);
    }

    public CompletableFuture<Boolean> placeBidOnAuction(User currentUser, Auction auction, long newMaxBid, boolean isBot) {
        if (currentUser.isBlocked()) {
            log.warn("[BLOCK-INTERCEPT] placeBidOnAuction denied: user '{}' (id={}) is BLOCKED.", currentUser.getUserName(), currentUser.getId());
            if (!isBot) {
                ClientManager.sendToUser(currentUser.getId(), "ERROR", new ErrorPayload(ERR_CODE_BLOCKED, MSG_ACCOUNT_BLOCKED));
            }
            return CompletableFuture.completedFuture(false);
        }

        if (auction.getSeller().getId().equals(currentUser.getId())) {
            log.warn("Bid rejected: seller {} cannot bid on their own auction {}", currentUser.getId(), auction.getId());
            return CompletableFuture.completedFuture(false);
        }

        Callable<String> bidTask = () -> {
            long expectedPrice;
            long expectedMaxBid;

            synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                boolean isActive = Auction.STATUS_RUNNING.equals(auction.getStatus())
                        || Auction.STATUS_WAITING_FOR_BID.equals(auction.getStatus());
                if (!isActive) {
                    return "NOT_RUNNING";
                }
                expectedPrice = auction.getCurrentPrice();
                expectedMaxBid = auction.getHighestMaxBid();
            }

            String finalStatus = "CONFLICT";
            BidDAO.BidCommitResult commitResult = null;

            try (java.sql.Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    commitResult = bidDAO.executeBidTransactionSourceOfTruth(conn, auction.getId(), currentUser, newMaxBid, isBot);
                    if (commitResult != null) {
                        conn.commit();
                        finalStatus = "SUCCESS";
                    } else {
                        conn.rollback();
                    }
                } catch (BidDAO.InsufficientFundsException e) {
                    conn.rollback();
                    finalStatus = "INSUFFICIENT_FUNDS";
                } catch (java.sql.SQLException e) {
                    conn.rollback();
                    finalStatus = "SQL_ERROR";
                    log.error("SQL error placing bid for auction {}: {}", auction.getId(), e.getMessage(), e);
                }
            } catch (java.sql.SQLException e) {
                finalStatus = "SQL_ERROR";
                log.error("DB connection error placing bid: {}", e.getMessage(), e);
            }

            if ("SUCCESS".equals(finalStatus) && commitResult != null) {
                final BidDAO.BidCommitResult committed = commitResult;
                synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                    if (auction.getCurrentPrice() <= committed.newCurrentPrice) {
                        User winner = committed.newWinnerId != null ? new User() : null;
                        if (winner != null) winner.setId(committed.newWinnerId);

                        /*
                         * FIX #7 (MEDIUM): The unsafe (long) casts of committed.newHighestMaxBid and
                         * committed.newCurrentPrice have been removed. Both are now correctly typed as
                         * long in BidCommitResult, so no cast is necessary.
                         */
                        auction.applyBidResult(currentUser, new Auction.BidResult(
                                winner,
                                committed.newHighestMaxBid,
                                committed.newCurrentPrice,
                                committed.newEndTime,
                                Auction.STATUS_WAITING_FOR_BID.equals(auction.getStatus())
                        ));
                    }
                }
                log.info("Bid committed: user={}, auction={}, newPrice={}", currentUser.getUserName(), auction.getId(), commitResult.newCurrentPrice);
            }
            return finalStatus;
        };

        return TransactionManager.submitTask(bidTask)
                .thenApply(result -> {
                    switch (result) {
                        case "SUCCESS" -> {
                            Map<String, Object> update = new HashMap<>();
                            update.put("auctionId", auction.getId());
                            update.put("newPrice", auction.getCurrentPrice());
                            update.put("winnerName", currentUser.getUserName());
                            update.put("newEndTime", auction.getEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());

                            try {
                                ObjectMapper mapper = JacksonConfig.mapper();
                                String jsonPayload = mapper.writeValueAsString(new NetworkMessage("UPDATE_AUCTION_PRICE", update));
                                ClientManager.publishAuctionUpdate(auction.getId(), jsonPayload);
                            } catch (JsonProcessingException e) {
                                ClientManager.broadcast("UPDATE_AUCTION_PRICE", update, null);
                            }

                            if (!isBot) AutoBidEngine.triggerBotScan(auction);
                            return true;
                        }
                        case "NOT_RUNNING" -> {
                            if (!isBot) ClientManager.sendToUser(currentUser.getId(), "ERROR", "Phiên đấu giá này đã đóng, không thể đặt giá nữa!");
                            return false;
                        }
                        case "INSUFFICIENT_FUNDS" -> {
                            if (!isBot) ClientManager.sendToUser(currentUser.getId(), "ERROR", "Số dư khả dụng không đủ để thực hiện đặt giá!");
                            return false;
                        }
                        default -> {
                            if (!isBot) ClientManager.sendToUser(currentUser.getId(), "ERROR", "Lỗi đặt giá hoặc giá trị đã thay đổi. Vui lòng thử lại!");
                            return false;
                        }
                    }
                })
                .exceptionally(ex -> {
                    log.error("Uncaught error during placeBidOnAuction task for auction {}: {}", auction.getId(), ex.getMessage(), ex);
                    return false;
                });
    }

    /**
     * Registers an auto-bid for the given user on the specified auction.
     *
     * <p>The database upsert (via {@link BidDAO#saveAutoBid}) and the in-memory
     * registration (via {@link model.auction.Auction#registerAutoBid}) are wrapped
     * inside a single database transaction. If the in-memory step fails the
     * connection is rolled back before the method returns, preventing orphaned
     * wallet locks.
     *
     * @param currentUser the authenticated bidder
     * @param auction     the live auction aggregate
     * @param maxBid      the maximum amount the bidder authorises
     * @param increment   the per-round bid step
     * @return a {@link CompletableFuture} resolving to {@code true} on full success
     */
    public CompletableFuture<Boolean> setupAutoBid(User currentUser, Auction auction,
                                                   long maxBid, long increment) {
        if (currentUser.isBlocked()) {
            log.warn("[BLOCK-INTERCEPT] setupAutoBid denied: user '{}' (id={}) is BLOCKED.",
                    currentUser.getUserName(), currentUser.getId());
            ClientManager.sendToUser(currentUser.getId(), "ERROR",
                    new ErrorPayload(ERR_CODE_BLOCKED, MSG_ACCOUNT_BLOCKED));
            return CompletableFuture.completedFuture(false);
        }

        if (auction.getSeller().getId().equals(currentUser.getId())) {
            return CompletableFuture.completedFuture(false);
        }

        Callable<Boolean> task = () -> {
            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // Step 1 — persist to DB (wallet lock applied inside, same conn)
                    bidDAO.saveAutoBid(conn, currentUser, auction, maxBid, increment);

                    // Step 2 — register in RAM; must succeed before we commit
                    boolean registered;
                    synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                        registered = auction.registerAutoBid(currentUser, maxBid, increment);
                    }

                    if (!registered) {
                        // In-memory guard rejected the bid (e.g. auction ended mid-flight)
                        conn.rollback();
                        log.warn("[AutoBid] registerAutoBid rejected for user={} auction={} — rolling back.",
                                currentUser.getUserName(), auction.getId());
                        return false;
                    }

                    conn.commit();
                    log.info("[AutoBid] Committed: user={}, auction={}, maxBid={}",
                            currentUser.getUserName(), auction.getId(), maxBid);
                    return true;

                } catch (AutoBidLockService.InsufficientFundsException e) {
                    conn.rollback();
                    log.warn("[AutoBid] Insufficient funds for user={} auction={}: {}",
                            currentUser.getUserName(), auction.getId(), e.getMessage());
                    return false;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            }
        };

        return TransactionManager.submitTask(task)
                .thenApply(success -> {
                    if (success) AutoBidEngine.triggerBotScan(auction);
                    return success;
                })
                .exceptionally(ex -> {
                    log.error("[AutoBid] Uncaught error for auction {}: {}",
                            auction.getId(), ex.getMessage(), ex);
                    return false;
                });
    }

    public CompletableFuture<Boolean> cancelAutoBid(User currentUser, Auction auction) {
        Callable<Boolean> task = () -> {
            try {
                if (bidDAO.cancelAutoBid(currentUser, auction)) {
                    synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                        auction.getActiveAutoBids().removeIf(ab -> ab.getBidder().getId().equals(currentUser.getId()));
                    }
                    return true;
                }
                return false;
            } catch (java.sql.SQLException e) {
                log.error("Failed to cancel auto-bid for user {} on auction {}: {}", currentUser.getUserName(), auction.getId(), e.getMessage(), e);
                return false;
            }
        };
        return TransactionManager.submitTask(task)
                .exceptionally(ex -> {
                    log.error("Uncaught error during cancelAutoBid for auction {}: {}", auction.getId(), ex.getMessage(), ex);
                    return false;
                });
    }
}