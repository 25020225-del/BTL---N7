package service;

import database.DatabaseManager;
import database.TransactionManager;
import database.dao.AuctionDAO;
import database.dao.WalletDAO;
import model.auction.Auction;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Service xử lý các tác vụ Admin liên quan đến phiên đấu giá.
 *
 * <h2>Trách nhiệm chính</h2>
 * <ul>
 *   <li>Hủy phiên đấu giá đang diễn ra ({@link #cancelAuctionAndRefund}).</li>
 *   <li>Đảm bảo toàn bộ tiền bị lock của người tham gia được giải phóng
 *       trong một DB transaction duy nhất, không rò rỉ tài chính.</li>
 * </ul>
 *
 * <h2>Mô hình lock tiền trong hệ thống</h2>
 * <pre>
 *   Manual Winner (không AutoBid):
 *     wallets.locked_balance += auctions.highest_max_bid
 *     (lock bởi BidDAO.executeBidTransactionSourceOfTruth khi isBot=false)
 *
 *   AutoBid Winner:
 *     wallets.locked_balance += auto_bids.max_bid
 *     (lock bởi BidDAO.saveAutoBid via AutoBidLockService.applyLockDifference)
 *
 *   Losing AutoBidders (kể cả khi bị manual bid đè):
 *     wallets.locked_balance += auto_bids.max_bid (is_active=1)
 *     (KHÔNG được unlock khi bị đè vì wasPreviousWinnerBot=true)
 * </pre>
 *
 * <h2>Thuật toán Cancel an toàn — tránh double-unlock</h2>
 * <pre>
 *   1. CAS update: auctions.status → CANCELLED (chỉ từ trạng thái hủy được)
 *   2. Đọc winner + highest_max_bid snapshot
 *   3. Nếu winner tồn tại VÀ không có active AutoBid:
 *        → Đây là Manual Winner → unlock highest_max_bid
 *   4. Sweep tất cả auto_bids.is_active=1:
 *        → Unlock max_bid cho từng user (bao gồm AutoBid winner + losers)
 *   5. SET auto_bids.is_active = 0 (soft-delete hàng loạt)
 *   6. Commit hoặc Rollback toàn bộ nếu có lỗi
 * </pre>
 */
public class AdminAuctionService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuctionService.class);

    // Các trạng thái có thể bị Admin hủy
    private static final String[] CANCELLABLE_STATUSES = {
            Auction.STATUS_OPEN,
            Auction.STATUS_WAITING_FOR_BID,
            Auction.STATUS_RUNNING
    };

    private final AuctionDAO auctionDAO;
    private final WalletDAO  walletDAO;

    public AdminAuctionService(AuctionDAO auctionDAO, WalletDAO walletDAO) {
        this.auctionDAO = auctionDAO;
        this.walletDAO  = walletDAO;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Kết quả trả về — tránh dùng Exception cho flow-control
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Kết quả của thao tác hủy phiên đấu giá.
     *
     * <ul>
     *   <li>{@link #SUCCESS}          — Hủy thành công, toàn bộ tiền đã hoàn.</li>
     *   <li>{@link #NOT_FOUND}        — Phiên không tồn tại trong DB.</li>
     *   <li>{@link #NOT_CANCELLABLE}  — Phiên đang ở trạng thái không cho phép hủy
     *                                   (ví dụ FINISHED, PAID, đã CANCELLED rồi).</li>
     *   <li>{@link #DB_ERROR}         — Lỗi DB, toàn bộ giao dịch đã rollback.</li>
     * </ul>
     */
    public enum CancelResult {
        SUCCESS,
        NOT_FOUND,
        NOT_CANCELLABLE,
        DB_ERROR
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Hủy phiên đấu giá và hoàn toàn bộ tiền bị lock cho những người tham gia.
     *
     * <p><b>Điều kiện tiên quyết:</b> Phiên phải đang ở trạng thái
     * {@code OPEN}, {@code WAITING_FOR_BID}, hoặc {@code RUNNING}.</p>
     *
     * <p><b>Đảm bảo tài chính (Financial Safety Guarantee):</b>
     * Toàn bộ thao tác tài chính — bao gồm unlock cho Manual Winner
     * và tất cả AutoBid user — nằm trong <em>một</em> JDBC transaction duy nhất
     * với {@code autoCommit=false}. Bất kỳ lỗi nào xảy ra với bất kỳ user nào
     * sẽ trigger {@code conn.rollback()} ngay lập tức, đảm bảo trạng thái
     * không bao giờ bị nửa vời.</p>
     *
     * @param auctionId ID của phiên đấu giá cần hủy.
     * @return {@link CancelResult} mô tả kết quả của thao tác.
     */
    public CancelResult cancelAuctionAndRefund(String auctionId) {
        log.info("[CANCEL] Admin initiated cancel for auction: {}", auctionId);

        // ── Bước 0: Kiểm tra nhanh phiên có tồn tại trong RAM không ──────────
        // (Tối ưu: tránh mở connection DB nếu phiên không tồn tại ở đâu cả)
        Auction ramAuction = AuctionManager.getAuctionList().stream()
                .filter(a -> auctionId.equals(a.getId()))
                .findFirst()
                .orElse(null);
        // ramAuction có thể null nếu phiên chưa được load vào RAM (vd: OPEN chưa đến giờ)
        // → vẫn cần kiểm tra DB làm source-of-truth

        try (Connection conn = DatabaseManager.getConnection()) {

            // Tắt auto-commit để kiểm soát transaction thủ công
            conn.setAutoCommit(false);

            try {

                // ── Bước 1: Đọc snapshot từ DB để lấy thông tin winner ──────────
                // Dùng một query duy nhất để tránh TOCTOU race condition
                AuctionSnapshot snapshot = readAuctionSnapshot(conn, auctionId);

                if (snapshot == null) {
                    conn.rollback(); // không cần, nhưng tốt để luôn clean up
                    log.warn("[CANCEL] Auction not found in DB: {}", auctionId);
                    return CancelResult.NOT_FOUND;
                }

                // ── Bước 2: CAS Update — chỉ hủy từ trạng thái hợp lệ ────────────
                // Dùng IN clause để server chấp nhận tất cả trạng thái cancellable
                // Nếu executeUpdate() = 0 → phiên đã FINISHED/PAID/CANCELLED → từ chối
                int updatedRows = markAuctionAsCancelled(conn, auctionId);
                if (updatedRows == 0) {
                    conn.rollback();
                    log.warn("[CANCEL] Auction {} is not in a cancellable state (status={})",
                            auctionId, snapshot.currentStatus);
                    return CancelResult.NOT_CANCELLABLE;
                }
                log.info("[CANCEL] Auction {} status → CANCELLED (was: {})",
                        auctionId, snapshot.currentStatus);

                // ── Bước 3: Hoàn tiền cho Manual Winner (nếu có) ─────────────────
                // Điều kiện: có winner VÀ winner KHÔNG có active AutoBid
                // (nếu winner là AutoBid user → sẽ được hoàn ở Bước 4)
                if (snapshot.winnerUserId != null && snapshot.highestMaxBid > 0) {
                    boolean winnerHasAutoBid = checkWinnerHasActiveAutoBid(
                            conn, auctionId, snapshot.winnerUserId);

                    if (!winnerHasAutoBid) {
                        // Manual winner: unlock toàn bộ highest_max_bid
                        refundManualWinner(conn, auctionId,
                                snapshot.winnerUserId, snapshot.highestMaxBid);
                        log.info("[CANCEL] Manual winner {} refunded {} VND for auction {}",
                                snapshot.winnerUserId, snapshot.highestMaxBid, auctionId);
                    } else {
                        log.debug("[CANCEL] Winner {} is AutoBid user — skip manual unlock, " +
                                "will be covered by AutoBid sweep.", snapshot.winnerUserId);
                    }
                } else {
                    log.info("[CANCEL] Auction {} had no winning bidder — skipping manual refund.", auctionId);
                }

                // ── Bước 4: Sweep toàn bộ AutoBid user đang is_active=1 ───────────
                // Bao gồm: AutoBid winner + tất cả losers có tiền còn bị lock
                // HIỆU NĂNG: Dùng batch query thay vì N individual queries
                List<AutoBidRecord> activeBids = fetchAllActiveAutoBids(conn, auctionId);

                log.info("[CANCEL] Found {} active AutoBid user(s) to refund for auction {}",
                        activeBids.size(), auctionId);

                for (AutoBidRecord record : activeBids) {
                    if (record.maxBid <= 0) {
                        log.warn("[CANCEL] Skipping zero-amount AutoBid for user {} on auction {}",
                                record.userId, auctionId);
                        continue;
                    }

                    // Mỗi lần unlock thất bại sẽ ném SQLException → trigger rollback toàn bộ
                    refundAutoBidUser(conn, auctionId, record.userId, record.maxBid);
                    log.info("[CANCEL] AutoBid user {} refunded {} VND for auction {}",
                            record.userId, record.maxBid, auctionId);
                }

                // ── Bước 5: Vô hiệu hóa toàn bộ AutoBid của phiên (soft-delete hàng loạt) ──
                // Một UPDATE duy nhất, O(1) thay vì N updates
                deactivateAllAutoBids(conn, auctionId);

                // ── Bước 6: COMMIT — chỉ gọi nếu TẤT CẢ các bước trên thành công ──
                conn.commit();

                log.info("[CANCEL] ✅ Auction {} fully cancelled and all {} user(s) refunded. COMMIT OK.",
                        auctionId, activeBids.size());

                // ── Bước 7: Đồng bộ RAM sau khi DB đã commit thành công ───────────
                // Chỉ sửa RAM sau commit để tránh inconsistent state khi rollback
                syncRamAfterSuccessfulCancel(auctionId, ramAuction);

                return CancelResult.SUCCESS;

            } catch (SQLException e) {
                // ROLLBACK TOÀN BỘ — đảm bảo không user nào bị mất tiền nửa chừng
                safeRollback(conn, auctionId, e);
                return CancelResult.DB_ERROR;
            }

        } catch (SQLException e) {
            // Lỗi ngay khi lấy connection — không có gì để rollback
            log.error("[CANCEL] Failed to obtain DB connection for auction {}: {}",
                    auctionId, e.getMessage(), e);
            return CancelResult.DB_ERROR;
        }
    }

    /**
     * Phiên bản bất đồng bộ — trả về {@link CompletableFuture} để tích hợp
     * với {@link TransactionManager} và không block luồng xử lý lệnh WebSocket.
     *
     * <p>Sử dụng trong {@code AdminActionHandler} khi nhận lệnh {@code CANCEL_AUCTION}.</p>
     *
     * @param auctionId ID phiên cần hủy.
     * @return Future chứa {@link CancelResult}.
     */
    public CompletableFuture<CancelResult> cancelAuctionAndRefundAsync(String auctionId) {
        Callable<CancelResult> task = () -> cancelAuctionAndRefund(auctionId);
        return TransactionManager.submitTask(task);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS — DB Operations (nhận conn từ caller)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Đọc snapshot tối thiểu từ DB — winner + highest_max_bid + status hiện tại.
     *
     * <p><b>HIỆU NĂNG SQLite:</b> SELECT chỉ 3 cột cần thiết, không SELECT *.
     * Không dùng JOIN để tránh full-scan trên bảng lớn.</p>
     *
     * @return {@link AuctionSnapshot} hoặc {@code null} nếu không tìm thấy.
     */
    private AuctionSnapshot readAuctionSnapshot(Connection conn,
                                                String auctionId) throws SQLException {
        final String sql =
                "SELECT status, winning_bidder_id, highest_max_bid "
                        + "FROM auctions WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                AuctionSnapshot snap    = new AuctionSnapshot();
                snap.currentStatus      = rs.getString("status");
                snap.winnerUserId       = rs.getString("winning_bidder_id"); // nullable
                snap.highestMaxBid      = rs.getLong("highest_max_bid");
                return snap;
            }
        }
    }

    /**
     * Thực hiện CAS Update: chuyển status → CANCELLED.
     *
     * <p>Sử dụng {@code IN (?,?,?)} thay vì nhiều OR để SQLite có thể dùng index trên cột status
     * hiệu quả hơn. Trả về số row bị ảnh hưởng để caller phát hiện conflict.</p>
     *
     * @return Số row được UPDATE (1 = thành công, 0 = phiên không ở trạng thái hủy được).
     */
    private int markAuctionAsCancelled(Connection conn,
                                       String auctionId) throws SQLException {
        // IN clause với placeholders rõ ràng — tránh string concatenation (SQL injection)
        final String sql =
                "UPDATE auctions SET status = ? "
                        + "WHERE id = ? AND status IN (?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Auction.STATUS_CANCELED);
            ps.setString(2, auctionId);
            ps.setString(3, Auction.STATUS_OPEN);
            ps.setString(4, Auction.STATUS_WAITING_FOR_BID);
            ps.setString(5, Auction.STATUS_RUNNING);
            return ps.executeUpdate();
        }
    }

    /**
     * Kiểm tra xem người thắng hiện tại có đang bật AutoBid không.
     *
     * <p>Dùng {@code SELECT 1} thay vì {@code SELECT *} để tối ưu — chỉ cần biết
     * có tồn tại hay không, không cần lấy dữ liệu.</p>
     *
     * @return {@code true} nếu winner có active AutoBid.
     */
    private boolean checkWinnerHasActiveAutoBid(Connection conn,
                                                String auctionId,
                                                String winnerUserId) throws SQLException {
        final String sql =
                "SELECT 1 FROM auto_bids "
                        + "WHERE auction_id = ? AND bidder_id = ? AND is_active = 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            ps.setString(2, winnerUserId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next(); // true nếu có ít nhất 1 row
            }
        }
    }

    /**
     * Hoàn tiền cho Manual Winner: unlock {@code highestMaxBid} và ghi lịch sử giao dịch.
     *
     * <p><b>An toàn tài chính:</b> Nếu {@link WalletDAO#unlockBalance} thất bại
     * (số tiền lock trong DB bị lệch so với expected — hệ thống bug nghiêm trọng),
     * nó sẽ ném {@link SQLException} → caller catch → {@code conn.rollback()} ngay lập tức.</p>
     */
    private void refundManualWinner(Connection conn,
                                    String auctionId,
                                    String winnerUserId,
                                    long highestMaxBid) throws SQLException {
        String now = LocalDateTime.now().toString();

        walletDAO.unlockBalance(conn, winnerUserId, highestMaxBid);

        walletDAO.addTransaction(
                conn,
                "CANCEL-REFUND-WIN-" + UUID.randomUUID(),
                winnerUserId,
                highestMaxBid,
                "Refund: auction CANCELLED by Admin — manual bid reserve released for auction: " + auctionId,
                now
        );
    }

    /**
     * Lấy toàn bộ danh sách AutoBid đang active trong một phiên.
     *
     * <p><b>HIỆU NĂNG:</b> Một query duy nhất lấy tất cả, load vào List trong RAM rồi
     * xử lý tuần tự. Tránh N+1 queries. ResultSet được đóng trước khi thực hiện
     * các UPDATE tiếp theo (SQLite không hỗ trợ mở đồng thời nhiều Statement
     * tốt trong chế độ WAL khi cùng connection).</p>
     *
     * @return List các {@link AutoBidRecord} cần hoàn tiền.
     */
    private List<AutoBidRecord> fetchAllActiveAutoBids(Connection conn,
                                                       String auctionId) throws SQLException {
        final String sql =
                "SELECT bidder_id, max_bid "
                        + "FROM auto_bids "
                        + "WHERE auction_id = ? AND is_active = 1";

        List<AutoBidRecord> records = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AutoBidRecord rec = new AutoBidRecord();
                    rec.userId = rs.getString("bidder_id");
                    rec.maxBid = rs.getLong("max_bid");
                    records.add(rec);
                }
            }
        }
        // ResultSet đã đóng tại đây — an toàn để thực hiện UPDATE tiếp theo
        return records;
    }

    /**
     * Hoàn tiền AutoBid cho một user: unlock {@code maxBid} và ghi lịch sử giao dịch.
     *
     * <p>Được gọi trong vòng lặp — mỗi lần ném {@link SQLException} sẽ được
     * bubble up lên caller để trigger {@code conn.rollback()} toàn bộ transaction.</p>
     */
    private void refundAutoBidUser(Connection conn,
                                   String auctionId,
                                   String userId,
                                   long maxBid) throws SQLException {
        String now = LocalDateTime.now().toString();

        walletDAO.unlockBalance(conn, userId, maxBid);

        walletDAO.addTransaction(
                conn,
                "CANCEL-REFUND-AB-" + UUID.randomUUID(),
                userId,
                maxBid,
                "Refund: auction CANCELLED by Admin — auto-bid reserve released for auction: " + auctionId,
                now
        );
    }

    /**
     * Vô hiệu hóa (soft-delete) tất cả AutoBid của phiên trong một UPDATE duy nhất.
     *
     * <p>Tối ưu cho SQLite: một batch UPDATE thay vì N individual updates.
     * SQLite thực thi UPDATE không WHERE-indexed rất nhanh khi bảng có index trên
     * {@code auction_id}.</p>
     */
    private void deactivateAllAutoBids(Connection conn,
                                       String auctionId) throws SQLException {
        final String sql =
                "UPDATE auto_bids SET is_active = 0 WHERE auction_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, auctionId);
            int affected = ps.executeUpdate();
            log.debug("[CANCEL] Deactivated {} auto_bids row(s) for auction {}",
                    affected, auctionId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  RAM SYNCHRONIZATION — chỉ gọi SAU khi DB commit thành công
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Đồng bộ trạng thái RAM và broadcast đến client SAU KHI DB đã commit thành công.
     *
     * <p><b>Thứ tự quan trọng:</b> RAM luôn được cập nhật SAU DB, không bao giờ trước.
     * Nếu crash giữa chừng, AuctionMonitor sẽ sweep và tự sửa DB/RAM ở chu kỳ tiếp theo.</p>
     */
    private void syncRamAfterSuccessfulCancel(String auctionId, Auction ramAuction) {
        if (ramAuction != null) {
            synchronized (AuctionManager.getLockForAuction(auctionId)) {
                ramAuction.setStatus(Auction.STATUS_CANCELED);
                // Dọn sạch hàng đợi AutoBid trong RAM để AutoBidEngine không tiếp tục scan
                ramAuction.getActiveAutoBids().clear();
            }
            // Xóa khỏi danh sách theo dõi của AuctionMonitor
            AuctionManager.getAuctionList().remove(ramAuction);
            AuctionManager.removeAuctionLock(auctionId);
            log.info("[CANCEL] RAM state cleaned for auction {}", auctionId);
        }

        // Broadcast đến toàn bộ clients đang kết nối
        ClientManager.broadcast(
                "AUCTION_CANCELLED",
                Map.of(
                        "auctionId", auctionId,
                        "message",   "Phiên đấu giá đã bị Admin hủy. Tiền đặt giá sẽ được hoàn lại."
                ),
                null
        );
        log.info("[CANCEL] Broadcast AUCTION_CANCELLED for auction {}", auctionId);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Rollback an toàn — nuốt exception thứ cấp nếu rollback cũng thất bại
     * và log cả hai để không mất thông tin debug.
     */
    private void safeRollback(Connection conn, String auctionId, SQLException originalError) {
        log.error("[CANCEL] ❌ SQL error during cancel for auction {}. Initiating ROLLBACK. Cause: {}",
                auctionId, originalError.getMessage(), originalError);
        try {
            conn.rollback();
            log.warn("[CANCEL] ROLLBACK successful for auction {}. No financial data was mutated.",
                    auctionId);
        } catch (SQLException rollbackEx) {
            // Đây là tình huống nguy hiểm nhất: rollback cũng thất bại
            // Cần alert/alarm ngay lập tức (trong production: gửi PagerDuty, Sentry, v.v.)
            log.error("[CANCEL] ☠ CRITICAL: ROLLBACK FAILED for auction {}! " +
                            "Manual DB inspection required! RollbackError: {}",
                    auctionId, rollbackEx.getMessage(), rollbackEx);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  INNER DATA CLASSES — Value Objects thuần túy (không logic)
    // ═══════════════════════════════════════════════════════════════════

    /** Snapshot tối thiểu của phiên đấu giá cần để thực hiện cancel. */
    private static final class AuctionSnapshot {
        String currentStatus;
        String winnerUserId;   // nullable
        long   highestMaxBid;  // = 0 nếu chưa có bid
    }

    /** Bản ghi AutoBid đang active cần hoàn tiền. */
    private static final class AutoBidRecord {
        String userId;
        long   maxBid;
    }
}