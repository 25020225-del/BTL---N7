package controller;

import database.TransactionManager;
import database.dao.BidDAO;
import database.DatabaseManager;
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
 * Controller chịu trách nhiệm xử lý toàn bộ các nghiệp vụ đặt giá phía server.
 *
 * <h2>Các chức năng chính</h2>
 * <ul>
 *   <li>{@link #placeBidOnAuction} — Đặt giá thủ công hoặc tự động (Bot).</li>
 *   <li>{@link #setupAutoBid}     — Đăng ký / nâng cấp cấu hình Auto-Bid.</li>
 *   <li>{@link #cancelAutoBid}    — Hủy một Auto-Bid đang hoạt động.</li>
 * </ul>
 *
 * <h2>Bất biến về Thread-Safety (Architectural Invariants)</h2>
 * <ol>
 *   <li>Mọi thao tác IO (DB) phải chạy bên trong {@link TransactionManager#submitTask}
 *       để serialize trên DB-Worker Thread, tránh deadlock giữa các lệnh đặt giá.</li>
 *   <li>RAM Lock ({@code synchronized}) chỉ được giữ trong thời gian ngắn nhất có thể:
 *       <ul>
 *         <li>Phase 1 (READ)  — Lấy snapshot trạng thái để phát hiện sai sớm.</li>
 *         <li>Phase 2 (WRITE) — Đồng bộ RAM <em>sau khi</em> DB commit thành công.</li>
 *       </ul>
 *   </li>
 *   <li>Không bao giờ giữ RAM Lock trong khi thực hiện giao dịch DB — tránh
 *       Nested Lock dẫn đến Livelock / Convoy Effect.</li>
 * </ol>
 */
public class ServerBidderController {

    private static final Logger log = LoggerFactory.getLogger(ServerBidderController.class);

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
     * <h3>Quy trình 3 pha</h3>
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

        // Guard: Người bán không được tự đặt giá cho phiên của mình.
        if (auction.getSeller().getId().equals(currentUser.getId())) {
            log.warn("Bid rejected: seller {} cannot bid on their own auction {}",
                    currentUser.getId(), auction.getId());
            return CompletableFuture.completedFuture(false);
        }

        Callable<String> bidTask = () -> {
            long   expectedPrice;
            long   expectedMaxBid;
            String expectedWinnerId;

            // ── Phase 1: Snapshot trạng thái RAM (Critical Section tối thiểu) ──
            synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                if (!Auction.STATUS_RUNNING.equals(auction.getStatus())) {
                    log.warn("Bid rejected: auction {} is in status '{}'",
                            auction.getId(), auction.getStatus());
                    return "NOT_RUNNING";
                }
                expectedPrice    = auction.getCurrentPrice();
                expectedMaxBid   = auction.getHighestMaxBid();
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
                    // Double-check: chỉ cập nhật nếu DB vẫn là nguồn dữ liệu mới hơn RAM.
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
                            // Broadcast giá mới đến toàn bộ clients đang theo dõi
                            Map<String, Object> update = new HashMap<>();
                            update.put("auctionId", auction.getId());
                            update.put("newPrice",  auction.getCurrentPrice());
                            update.put("winnerName", currentUser.getUserName());
                            update.put("newEndTime", auction.getEndTime()
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant().toEpochMilli());
                            ClientManager.broadcast("UPDATE_AUCTION_PRICE", update, null);

                            // Kích hoạt AutoBidEngine để các Bot đối thủ phản ứng
                            if (!isBot) {
                                AutoBidEngine.triggerBotScan(auction);
                            }
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
     * <p>Thứ tự thao tác được thiết kế để đảm bảo tính nhất quán DB-RAM:</p>
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

        if (auction.getSeller().getId().equals(currentUser.getId())) {
            log.warn("Auto-bid rejected: seller {} cannot set auto-bid on own auction",
                    currentUser.getId());
            return CompletableFuture.completedFuture(false);
        }

        Callable<Boolean> task = () -> {
            boolean saved;
            try {
                // DB write nằm ngoài RAM Lock — không cản trở đọc đồng thời
                saved = bidDAO.saveAutoBid(currentUser, auction, maxBid, increment);
            } catch (SQLException e) {
                log.error("Failed to persist auto-bid for user {} on auction {}: {}",
                        currentUser.getUserName(), auction.getId(), e.getMessage(), e);
                return false;
            }

            if (saved) {
                // Cập nhật hàng đợi RAM trong Critical Section ngắn
                synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                    boolean registered = auction.registerAutoBid(currentUser, maxBid, increment);
                    if (registered) {
                        log.info("Auto-bid registered in RAM: user={}, auction={}, maxBid={}",
                                currentUser.getUserName(), auction.getId(), maxBid);
                        return true;
                    }
                    // Auction vừa đóng trong khoảnh khắc DB ghi xong — trả về false nhưng
                    // không cần rollback DB vì BidDAO.cancelAutoBid sẽ dọn dẹp khi auction kết thúc.
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
    // CANCEL AUTO-BID  ← [BUG FIX]: Method này bị thiếu khiến BidActionHandler
    //                    không compile được. BidActionHandler.handleSetupAutoBid()
    //                    (khi maxBid == 0) gọi bidderCtrl.cancelAutoBid(user, auction)
    //                    và chain .thenAccept(), nên kiểu trả về phải là
    //                    CompletableFuture<Boolean>, không phải boolean blocking.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Hủy một Auto-Bid đang hoạt động của người dùng trên phiên đấu giá và giải phóng
     * các khoản tiền đã bị khóa liên quan, theo mô hình DB-first / RAM-second.
     *
     * <h3>Quy trình</h3>
     * <ol>
     *   <li><b>DB cancel</b>  — Soft-delete bản ghi {@code auto_bids}, giải phóng
     *       {@code locked_balance} qua {@link BidDAO#cancelAutoBid}.</li>
     *   <li><b>RAM purge</b>  — Xóa {@link model.auction.AutoBid} tương ứng khỏi
     *       {@code auction.activeAutoBids} trong Critical Section ngắn.</li>
     * </ol>
     *
     * <h3>Thread-Safety</h3>
     * <ul>
     *   <li>Toàn bộ DB write chạy trên DB-Worker Thread của {@link TransactionManager}.</li>
     *   <li>{@code PriorityBlockingQueue.removeIf()} là thread-safe; RAM Lock chỉ bảo
     *       vệ thêm tính nguyên tử giữa việc xóa và trạng thái phiên.</li>
     *   <li>Nếu DB thất bại, RAM không bao giờ bị thay đổi → không có trạng thái
     *       không nhất quán giữa DB và RAM.</li>
     * </ul>
     *
     * @param currentUser Người dùng yêu cầu hủy Auto-Bid (đã xác thực).
     * @param auction     Phiên đấu giá chứa Auto-Bid cần hủy.
     * @return {@link CompletableFuture}{@code <Boolean>}:
     *         <ul>
     *           <li>{@code true}  — Hủy thành công (DB soft-deleted + RAM removed).</li>
     *           <li>{@code false} — Không tìm thấy Auto-Bid đang hoạt động, hoặc lỗi DB.</li>
     *         </ul>
     */
    public CompletableFuture<Boolean> cancelAutoBid(User currentUser, Auction auction) {

        Callable<Boolean> task = () -> {
            boolean cancelled;
            try {
                // ── Bước 1: Xóa mềm (soft-delete) trong DB và giải phóng ví ────
                // bidDAO.cancelAutoBid() thực hiện:
                //   a) Check auto_bids có tồn tại không (nếu không → return false)
                //   b) Gọi autoBidLockService.releaseAllLocks() để cộng lại locked_balance
                //   c) UPDATE auto_bids SET is_active = 0 (soft-delete)
                //   d) Commit — toàn bộ trong 1 JDBC transaction ACID
                cancelled = bidDAO.cancelAutoBid(currentUser, auction);
            } catch (SQLException e) {
                log.error("DB error cancelling auto-bid: user={}, auction={}: {}",
                        currentUser.getUserName(), auction.getId(), e.getMessage(), e);
                return false;
            }

            if (!cancelled) {
                // Không có bản ghi is_active = 1 trong DB → không có gì để hủy
                log.warn("Cancel auto-bid: no active auto-bid found for user={} on auction={}",
                        currentUser.getUserName(), auction.getId());
                return false;
            }

            // ── Bước 2: Xóa khỏi hàng đợi RAM trong Critical Section ─────────
            // PriorityBlockingQueue.removeIf() tự thread-safe, nhưng synchronized
            // ở đây đảm bảo tính nguyên tử giữa việc xóa và kiểm tra trạng thái phiên,
            // tránh race condition với AutoBidEngine đang đọc hàng đợi song song.
            synchronized (AuctionManager.getLockForAuction(auction.getId())) {
                boolean removedFromRam = auction.getActiveAutoBids()
                        .removeIf(ab -> ab.getBidder().getId().equals(currentUser.getId()));

                if (removedFromRam) {
                    log.info("Auto-bid cancelled and removed from RAM: user={}, auction={}",
                            currentUser.getUserName(), auction.getId());
                } else {
                    // Trường hợp biên: DB có bản ghi nhưng RAM đã bị dọn trước (ví dụ:
                    // phiên vừa kết thúc và AuctionMonitor đã clear hàng đợi).
                    // Vẫn trả về true vì DB đã hủy thành công — mục tiêu chính đã đạt.
                    log.warn("Auto-bid cancelled in DB but entry was not found in RAM queue "
                                    + "(auction may have just closed): user={}, auction={}",
                            currentUser.getUserName(), auction.getId());
                }
            }

            return true;
        };

        return TransactionManager.submitTask(task)
                .exceptionally(ex -> {
                    log.error("Uncaught error during cancelAutoBid for user={}, auction={}: {}",
                            currentUser.getUserName(), auction.getId(), ex.getMessage(), ex);
                    return false;
                });
    }
}