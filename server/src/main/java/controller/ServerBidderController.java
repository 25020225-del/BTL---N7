package controller;

// ════════════════════════════════════════════════════════════════════════════
// FILE: server/src/main/java/controller/ServerBidderController.java
// THAY ĐỔI: Thêm guard isBlocked() vào ĐẦU method placeBidOnAuction() và
//           setupAutoBid(). Không thay đổi bất kỳ logic nào bên dưới guard.
// ════════════════════════════════════════════════════════════════════════════

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
import utils.JacksonConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Controller chịu trách nhiệm xử lý toàn bộ các nghiệp vụ đặt giá phía server.
 *
 * <h2>Tính năng mới — Cơ chế chặn tài khoản bị khóa (Block Interception)</h2>
 * <p>Cả hai entry-point đặt giá ({@link #placeBidOnAuction} và {@link #setupAutoBid})
 * đều kiểm tra {@link User#isBlocked()} ngay tại dòng đầu tiên, <em>trước khi</em>
 * bất kỳ tác vụ DB nào được khởi tạo. Nếu tài khoản bị khóa:</p>
 * <ul>
 *   <li>Request bị từ chối ngay lập tức với {@link CompletableFuture#completedFuture}
 *       (không tốn DB connection, không tốn thread pool slot).</li>
 *   <li>Một thông báo lỗi được gửi về client qua {@link ClientManager#sendToUser}
 *       với nội dung rõ ràng về nguyên nhân.</li>
 *   <li>Không có nhánh code nào bên dưới guard được thực thi — an toàn tuyệt đối.</li>
 * </ul>
 *
 * <h2>Các chức năng chính</h2>
 * <ul>
 *   <li>{@link #placeBidOnAuction} — Đặt giá thủ công hoặc tự động (Bot).</li>
 *   <li>{@link #setupAutoBid}     — Đăng ký / nâng cấp cấu hình Auto-Bid.</li>
 *   <li>{@link #cancelAutoBid}    — Hủy một Auto-Bid đang hoạt động.</li>
 * </ul>
 */
public class ServerBidderController {

    private static final Logger log = LoggerFactory.getLogger(ServerBidderController.class);

    /**
     * Thông báo lỗi chuẩn hoá trả về khi phát hiện tài khoản bị khóa.
     * Được tách thành hằng số để dễ kiểm tra trong unit test và tập trung
     * điểm thay đổi nội dung thông báo.
     */
    static final String MSG_ACCOUNT_BLOCKED =
            "Tài khoản của bạn đã bị Quản trị viên khóa, "
                    + "không thể thực hiện thao tác này.";

    /**
     * Mã lỗi dành riêng cho trường hợp tài khoản bị khóa cố đặt giá.
     */
    static final String ERR_CODE_BLOCKED = "ERR_BID_403";

    private final BidDAO bidDAO;

    /**
     * Khởi tạo controller với DAO duy nhất qua Dependency Injection.
     *
     * @param bidDAO DAO thực hiện mọi thao tác DB liên quan đến lệnh đặt giá.
     */
    public ServerBidderController(BidDAO bidDAO) {
        this.bidDAO = bidDAO;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PLACE BID (manual / bot)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Thực thi một lệnh đặt giá bất đồng bộ, sử dụng mô hình Optimistic Locking hai pha.
     *
     * <h3>Guard tầng 0 — Kiểm tra tài khoản bị khóa (MỚI)</h3>
     * <p>Đây là kiểm tra <strong>đầu tiên</strong> và <strong>dứt khoát</strong>.
     * Nếu {@code currentUser.isBlocked()} trả về {@code true}, toàn bộ phương thức
     * dừng lại ngay — không khởi tạo {@link Callable}, không lấy DB connection,
     * không truy cập RAM Lock. Điều này đảm bảo tài khoản bị khóa không thể len lỏi
     * qua bất kỳ đường dẫn code nào.</p>
     *
     * <h3>Guard tầng 1 — Người bán không được tự đặt giá</h3>
     * <p>Guard hiện có, giữ nguyên sau guard tầng 0.</p>
     *
     * <h3>Quy trình 3 pha (giữ nguyên)</h3>
     * <ol>
     *   <li><b>Fast-Fail</b>  — Kiểm tra trạng thái RAM trong thời gian Lock tối thiểu.</li>
     *   <li><b>DB Write</b>   — Giao dịch DB độc lập không giữ RAM Lock.</li>
     *   <li><b>RAM Sync</b>   — Cập nhật RAM chỉ sau khi DB đã commit thành công.</li>
     * </ol>
     *
     * @param currentUser Người dùng đang đặt giá (đã xác thực).
     * @param auction     Phiên đấu giá mục tiêu.
     * @param newMaxBid   Mức giá tối đa người dùng sẵn sàng chi (VNĐ).
     * @param isBot       {@code true} nếu lệnh do Auto-Bid Engine kích hoạt.
     * @return {@link CompletableFuture}{@code <Boolean>} — {@code true} nếu đặt giá thành công.
     */
    public CompletableFuture<Boolean> placeBidOnAuction(
            User currentUser, Auction auction, long newMaxBid, boolean isBot) {

        // ════════════════════════════════════════════════════════════════════
        // GUARD 0: CHẶN TÀI KHOẢN BỊ KHÓA — DÒNG ĐẦU TIÊN, KHÔNG CÓ NGOẠI LỆ
        // ════════════════════════════════════════════════════════════════════
        // Kiểm tra in-memory trước (O(1), không tốn I/O).
        // currentUser.isBlocked() trả về true khi role == "BLOCKED" —
        // giá trị đã được ClientManager.updateBlockStatusInMemory() cập nhật
        // ngay khi Admin gửi lệnh BLOCK_USER.
        if (currentUser.isBlocked()) {
            log.warn("[BLOCK-INTERCEPT] placeBidOnAuction denied: user '{}' (id={}) is BLOCKED. "
                            + "Auction={}, requestedBid={}.",
                    currentUser.getUserName(), currentUser.getId(),
                    auction.getId(), newMaxBid);

            // Gửi thông báo lỗi rõ ràng về client.
            // isBot = true nghĩa là lệnh đến từ AutoBidEngine — không nên gửi
            // thông báo vì Bot đặt lệnh thay mặt user đang bị khóa:
            // AutoBidEngine nên bị dừng khi user bị khóa (xử lý riêng).
            if (!isBot) {
                ClientManager.sendToUser(currentUser.getId(), "ERROR",
                        new ErrorPayload(ERR_CODE_BLOCKED, MSG_ACCOUNT_BLOCKED));
            }

            // Trả về false ngay lập tức — KHÔNG chạy tiếp bất kỳ dòng nào.
            return CompletableFuture.completedFuture(false);
        }
        // ════════════════════════════════════════════════════════════════════
        // END GUARD 0
        // ════════════════════════════════════════════════════════════════════

        // Guard 1 (giữ nguyên): Người bán không được tự đặt giá cho phiên của mình.
        if (auction.getSeller().getId().equals(currentUser.getId())) {
            log.warn("Bid rejected: seller {} cannot bid on their own auction {}",
                    currentUser.getId(), auction.getId());
            return CompletableFuture.completedFuture(false);
        }

        // ── Phần còn lại KHÔNG THAY ĐỔI — giữ nguyên hoàn toàn ─────────────
        Callable<String> bidTask = () -> {
            long expectedPrice;
            long expectedMaxBid;
            String expectedWinnerId;

            // ── Phase 1: Snapshot trạng thái RAM (Critical Section tối thiểu) ──
            synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                if (!Auction.STATUS_RUNNING.equals(auction.getStatus())) {
                    log.warn("Bid rejected: auction {} is in status '{}'",
                            auction.getId(), auction.getStatus());
                    return "NOT_RUNNING";
                }
                expectedPrice = auction.getCurrentPrice();
                expectedMaxBid = auction.getHighestMaxBid();
                expectedWinnerId = auction.getWinningBidder() != null
                        ? auction.getWinningBidder().getId()
                        : null;
            }

            // ── Phase 2: Giao dịch DB (Optimistic Locking — không giữ RAM Lock) ──
            String finalStatus = "CONFLICT";
            BidDAO.BidCommitResult commitResult = null;

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    commitResult = bidDAO.executeBidTransactionSourceOfTruth(
                            conn,
                            auction.getId(),
                            currentUser,
                            newMaxBid,
                            isBot
                    );

                    if (commitResult != null) {
                        conn.commit();
                        finalStatus = "SUCCESS";
                    } else {
                        conn.rollback(); // Optimistic conflict — giá đã thay đổi bởi thread khác
                    }

                } catch (BidDAO.InsufficientFundsException e) {
                    conn.rollback();
                    finalStatus = "INSUFFICIENT_FUNDS";
                } catch (SQLException e) {
                    conn.rollback();
                    finalStatus = "SQL_ERROR";
                    log.error("SQL error placing bid for auction {}: {}", auction.getId(), e.getMessage(), e);
                }

            } catch (SQLException e) {
                finalStatus = "SQL_ERROR";
                log.error("DB connection error placing bid: {}", e.getMessage(), e);
            }

            // ── Phase 3: Đồng bộ RAM sau khi DB đã commit thành công ──────────
            if ("SUCCESS".equals(finalStatus) && commitResult != null) {
                final BidDAO.BidCommitResult committed = commitResult;
                synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                    if (auction.getCurrentPrice() <= committed.newCurrentPrice) {
                        User winner = null;
                        if (committed.newWinnerId != null) {
                            winner = new User();
                            winner.setId(committed.newWinnerId);
                        }
                        auction.applyBidResult(currentUser, new Auction.BidResult(
                                winner,
                                (long) committed.newHighestMaxBid,
                                (long) committed.newCurrentPrice,
                                committed.newEndTime,
                                Auction.STATUS_WAITING_FOR_BID.equals(auction.getStatus())
                        ));
                    }
                }
                log.info("Bid committed: user={}, auction={}, newPrice={}",
                        currentUser.getUserName(), auction.getId(), commitResult.newCurrentPrice);
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
                            update.put("newEndTime", auction.getEndTime()
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant().toEpochMilli());

                            try {
                                ObjectMapper mapper = JacksonConfig.mapper();
                                String jsonPayload = mapper.writeValueAsString(
                                        new NetworkMessage("UPDATE_AUCTION_PRICE", update));
                                ClientManager.publishAuctionUpdate(auction.getId(), jsonPayload);
                            } catch (JsonProcessingException e) {
                                log.error("Failed to serialize UPDATE_AUCTION_PRICE for auction {}: {}",
                                        auction.getId(), e.getMessage());
                                // Fallback: broadcast thông thường không có debounce
                                ClientManager.broadcast("UPDATE_AUCTION_PRICE", update, null);
                            }

                            if (!isBot) AutoBidEngine.triggerBotScan(auction);
                            return true;
                        }
                        case "NOT_RUNNING" -> {
                            if (!isBot) {
                                ClientManager.sendToUser(currentUser.getId(), "ERROR",
                                        "Phiên đấu giá này đã đóng, không thể đặt giá nữa!");
                            }
                            return false;
                        }
                        case "INSUFFICIENT_FUNDS" -> {
                            if (!isBot) {
                                ClientManager.sendToUser(currentUser.getId(), "ERROR",
                                        "Số dư khả dụng không đủ để thực hiện đặt giá!");
                            }
                            return false;
                        }
                        default -> {
                            if (!isBot) {
                                ClientManager.sendToUser(currentUser.getId(), "ERROR",
                                        "Lỗi đặt giá hoặc giá trị đã thay đổi. Vui lòng thử lại!");
                            }
                            return false;
                        }
                    }
                })
                .exceptionally(ex -> {
                    log.error("Uncaught error during placeBidOnAuction task for auction {}: {}",
                            auction.getId(), ex.getMessage(), ex);
                    return false;
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SETUP AUTO-BID
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Đăng ký hoặc nâng cấp cấu hình Auto-Bid cho người dùng trên một phiên đấu giá.
     *
     * <h3>Guard tầng 0 — Kiểm tra tài khoản bị khóa (MỚI)</h3>
     * <p>Kiểm tra <strong>dứt khoát</strong> tại dòng đầu tiên. Nếu
     * {@code currentUser.isBlocked()} trả về {@code true}, toàn bộ luồng đăng ký
     * Auto-Bid bị từ chối ngay — DB không được chạm tới, không có AutoBid nào
     * được ghi vào bảng {@code auto_bids}, không có lock tiền nào xảy ra.</p>
     *
     * <h3>Thứ tự thao tác (giữ nguyên)</h3>
     * <ol>
     *   <li>Persist vào DB trước (source of truth).</li>
     *   <li>Chỉ cập nhật hàng đợi RAM sau khi DB ghi thành công.</li>
     *   <li>Kích hoạt {@link AutoBidEngine} để scan ngay lập tức.</li>
     * </ol>
     *
     * @param currentUser Người dùng đăng ký Auto-Bid (đã xác thực).
     * @param auction     Phiên đấu giá mục tiêu.
     * @param maxBid      Mức giá tối đa (VNĐ, phải lớn hơn giá hiện tại).
     * @param increment   Bước tăng giá tối thiểu mỗi lần Bot kích hoạt (VNĐ, phải {@code > 0}).
     * @return {@link CompletableFuture}{@code <Boolean>} — {@code true} nếu đăng ký thành công.
     */
    public CompletableFuture<Boolean> setupAutoBid(
            User currentUser, Auction auction, long maxBid, long increment) {

        // ════════════════════════════════════════════════════════════════════
        // GUARD 0: CHẶN TÀI KHOẢN BỊ KHÓA — DÒNG ĐẦU TIÊN, KHÔNG CÓ NGOẠI LỆ
        // ════════════════════════════════════════════════════════════════════
        // Chặn trước khi bất kỳ logic nào chạy — kể cả guard "seller check".
        // Tài khoản bị khóa không được phép đăng ký/cập nhật AutoBid vì:
        //   1. Không được đặt giá => AutoBid cũng vô nghĩa.
        //   2. Cấm ghi bất kỳ bản ghi nào vào bảng auto_bids.
        //   3. Cấm khóa bất kỳ khoản tiền nào vào locked_balance.
        if (currentUser.isBlocked()) {
            log.warn("[BLOCK-INTERCEPT] setupAutoBid denied: user '{}' (id={}) is BLOCKED. "
                            + "Auction={}, requestedMaxBid={}.",
                    currentUser.getUserName(), currentUser.getId(),
                    auction.getId(), maxBid);

            ClientManager.sendToUser(currentUser.getId(), "ERROR",
                    new ErrorPayload(ERR_CODE_BLOCKED, MSG_ACCOUNT_BLOCKED));

            // Trả về false ngay lập tức — KHÔNG chạy tiếp bất kỳ dòng nào.
            return CompletableFuture.completedFuture(false);
        }
        // ════════════════════════════════════════════════════════════════════
        // END GUARD 0
        // ════════════════════════════════════════════════════════════════════

        // Guard 1 (giữ nguyên): Người bán không được tự đặt AutoBid cho phiên của mình.
        if (auction.getSeller().getId().equals(currentUser.getId())) {
            log.warn("Auto-bid rejected: seller {} cannot set auto-bid on own auction",
                    currentUser.getId());
            return CompletableFuture.completedFuture(false);
        }

        // ── Phần còn lại KHÔNG THAY ĐỔI — giữ nguyên hoàn toàn ─────────────
        Callable<Boolean> task = () -> {
            boolean saved;
            try {
                saved = bidDAO.saveAutoBid(currentUser, auction, maxBid, increment);
            } catch (SQLException e) {
                log.error("Failed to persist auto-bid for user {} on auction {}: {}",
                        currentUser.getUserName(), auction.getId(), e.getMessage(), e);
                return false;
            }

            if (saved) {
                synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                    boolean registered = auction.registerAutoBid(currentUser, maxBid, increment);
                    if (registered) {
                        log.info("Auto-bid registered in RAM: user={}, auction={}, maxBid={}",
                                currentUser.getUserName(), auction.getId(), maxBid);
                        return true;
                    }
                    log.warn("Auto-bid DB saved but auction {} closed before RAM registration",
                            auction.getId());
                }
            }
            return false;
        };

        return TransactionManager.submitTask(task)
                .thenApply(success -> {
                    if (success) {
                        AutoBidEngine.triggerBotScan(auction);
                    }
                    return success;
                })
                .exceptionally(ex -> {
                    log.error("Uncaught error during setupAutoBid for auction {}: {}",
                            auction.getId(), ex.getMessage(), ex);
                    return false;
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CANCEL AUTO-BID — KHÔNG THAY ĐỔI
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Hủy một Auto-Bid đang hoạt động của người dùng trên phiên đấu giá.
     * (Giữ nguyên hoàn toàn — không cần guard block vì hủy là thao tác an toàn.)
     */
    public CompletableFuture<Boolean> cancelAutoBid(User currentUser, Auction auction) {

        Callable<Boolean> task = () -> {
            boolean cancelled;
            try {
                cancelled = bidDAO.cancelAutoBid(currentUser, auction);
            } catch (SQLException e) {
                log.error("Failed to cancel auto-bid for user {} on auction {}: {}",
                        currentUser.getUserName(), auction.getId(), e.getMessage(), e);
                return false;
            }

            if (cancelled) {
                synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                    auction.getActiveAutoBids().removeIf(
                            ab -> ab.getBidder().getId().equals(currentUser.getId()));
                    log.info("Auto-bid cancelled in RAM: user={}, auction={}",
                            currentUser.getUserName(), auction.getId());
                }
                return true;
            }
            return false;
        };

        return TransactionManager.submitTask(task)
                .exceptionally(ex -> {
                    log.error("Uncaught error during cancelAutoBid for auction {}: {}",
                            auction.getId(), ex.getMessage(), ex);
                    return false;
                });
    }
}