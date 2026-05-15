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
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Controller responsible for handling bidding operations on the server side.
 */
public class ServerBidderController {

    private static final Logger log = LoggerFactory.getLogger(ServerBidderController.class);
    private final BidDAO bidDAO;

    public ServerBidderController(BidDAO bidDAO) {
        this.bidDAO = bidDAO;
    }

    public CompletableFuture<Boolean> placeBidOnAuction(User currentUser, Auction auction, long newMaxBid, boolean isBot) {

        if (auction.getSeller().getId().equals(currentUser.getId())) {
            log.warn("Cannot bid on own auction: {}", currentUser.getId());
            return CompletableFuture.completedFuture(false);
        }

        Callable<String> bidTask = () -> {
            BidDAO.BidCommitResult commitResult = null;
            String finalStatus = "CONFLICT";

            long expectedPrice;
            long expectedMaxBid;
            String expectedWinnerId;

            // [ARCHITECT FIX]: 1. THU HẸP PHẠM VI LOCK - FAST FAIL
            // Chỉ giữ Lock siêu ngắn để kiểm tra trạng thái RAM và lấy Snapshot dữ liệu.
            // Giải phóng Lock ngay lập tức để không cản trở các Request đọc khác.
            synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                if (!Auction.STATUS_RUNNING.equals(auction.getStatus())) {
                    log.warn("Bid rejected: Auction {} is currently in status {}", auction.getId(), auction.getStatus());
                    return "NOT_RUNNING";
                }
                expectedPrice = auction.getCurrentPrice();
                expectedMaxBid = auction.getHighestMaxBid();
                expectedWinnerId = auction.getWinningBidder() != null ? auction.getWinningBidder().getId() : null;
            }

            // [ARCHITECT FIX]: 2. GIAO DỊCH DATABASE ĐỘC LẬP
            // Không giữ RAM Lock ở khu vực này. Optimistic Locking của BidDAO sẽ tự lo việc tranh chấp.
            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    commitResult = bidDAO.executeBidTransactionSourceOfTruth(
                            conn, auction.getId(), currentUser, newMaxBid, expectedPrice, expectedMaxBid, expectedWinnerId, isBot
                    );

                    if (commitResult != null) {
                        conn.commit();
                        finalStatus = "SUCCESS";

                        // [ARCHITECT FIX]: 3. CẬP NHẬT RAM SAU KHI DB ĐÃ COMMIT THÀNH CÔNG
                        // Lúc này mới xin lại Lock để đồng bộ RAM với Source of Truth (Database).
                        synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                            if (auction.getCurrentPrice() <= (long) commitResult.newCurrentPrice) {
                                User winner = null;
                                if (commitResult.newWinnerId != null) {
                                    winner = new User();
                                    winner.setId(commitResult.newWinnerId);
                                }
                                Auction.BidResult ramResult = new Auction.BidResult(
                                        winner,
                                        (long) commitResult.newHighestMaxBid,
                                        (long) commitResult.newCurrentPrice,
                                        commitResult.newEndTime
                                );
                                auction.applyBidResult(currentUser, ramResult);
                            }
                        }
                    } else {
                        conn.rollback();
                    }
                } catch (BidDAO.InsufficientFundsException e) {
                    conn.rollback();
                    finalStatus = "INSUFFICIENT_FUNDS";
                } catch (SQLException e) {
                    conn.rollback();
                    finalStatus = "SQL_ERROR";
                    log.error("SQL Error during bid: {}", e.getMessage());
                }
            } catch (SQLException e) {
                finalStatus = "SQL_ERROR";
                log.error("DB Connection Error: {}", e.getMessage());
            }

            if ("SUCCESS".equals(finalStatus)) {
                log.info("Successfully placed bid for user {}", currentUser.getName());
            }
            return finalStatus;
        };

        return TransactionManager.submitTask(bidTask).thenApply(finalResult -> {
            if ("SUCCESS".equals(finalResult)) {
                Map<String, Object> updateData = new HashMap<>();
                updateData.put("auctionId", auction.getId());
                updateData.put("newPrice", auction.getCurrentPrice());
                updateData.put("winnerName", currentUser.getUserName());
                updateData.put("newEndTime", auction.getEndTime()
                        .atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli());

                ClientManager.broadcast("UPDATE_AUCTION_PRICE", updateData, null);

                if (!isBot) {
                    AutoBidEngine.triggerBotScan(auction);
                }
                return true;
            } else if ("NOT_RUNNING".equals(finalResult)) {
                if (!isBot) {
                    ClientManager.sendToUser(currentUser.getId(), "ERROR", "Phiên đấu giá này đã đóng, không thể đặt giá nữa!");
                }
                return false;
            } else if ("INSUFFICIENT_FUNDS".equals(finalResult)) {
                if (!isBot) {
                    ClientManager.sendToUser(currentUser.getId(), "ERROR", "Số dư khả dụng không đủ để thực hiện đặt giá!");
                }
                return false;
            } else {
                if (!isBot) {
                    ClientManager.sendToUser(currentUser.getId(), "ERROR", "Lỗi đặt giá hoặc giá trị đã thay đổi. Vui lòng thử lại!");
                }
                return false;
            }
        }).exceptionally(ex -> {
            log.error("The transaction could not be executed via the queue: {}", ex.getMessage());
            return false;
        });
    }

    public CompletableFuture<Boolean> setupAutoBid(User currentUser, Auction auction, long maxBid, long increment) {
        if (auction.getSeller().getId().equals(currentUser.getId())) {
            log.warn("Cannot set auto-bid on own auction");
            return CompletableFuture.completedFuture(false);
        }

        Callable<Boolean> saveAutoBidTask = () -> {
            boolean isDbSaved = false;
            try {
                // Thao tác DB nằm ngoài RAM Lock (rất chuẩn xác)
                isDbSaved = bidDAO.saveAutoBid(currentUser, auction, maxBid, increment);
            } catch (SQLException e) {
                log.error("Failed to save auto-bid config to DB: {}", e.getMessage());
                return false;
            }

            if (isDbSaved) {
                synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                    boolean ramSuccess = auction.registerAutoBid(currentUser, maxBid, increment);
                    if (ramSuccess) {
                        log.info("Auto-Bid Configuration for {} has been saved and registered.", currentUser.getUserName());
                        return true;
                    }
                }
            }
            return false;
        };

        return TransactionManager.submitTask(saveAutoBidTask).thenApply(success -> {
            if (success) {
                AutoBidEngine.triggerBotScan(auction);
            }
            return success;
        }).exceptionally(ex -> {
            log.error("Execution error while saving auto-bid: {}", ex.getMessage());
            return false;
        });
    }
}