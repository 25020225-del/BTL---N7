package controller;

import database.DatabaseManager;
import database.TransactionManager;
import database.dao.AuctionDAO;
import database.dao.UserDAO;
import database.dao.WalletDAO;
import database.dao.WithdrawalDAO;
import model.auction.Auction;
import model.user.Admin;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Controller xử lý các thao tác quản trị (Admin) phía server.
 *
 * <p>Ngoài các chức năng Admin hiện có (duyệt/từ chối đấu giá, quản lý user),
 * controller này được bổ sung logic <b>Checker</b> của mô hình Maker-Checker
 * để xử lý các yêu cầu rút tiền:</p>
 * <ul>
 *   <li>{@link #processWithdrawal(Admin, String, boolean)} — APPROVE hoặc REJECT.</li>
 *   <li>{@link #fetchPendingWithdrawals(Admin)} — Lấy danh sách chờ xử lý.</li>
 * </ul>
 */
public class ServerAdminController {

    private static final Logger log = LoggerFactory.getLogger(ServerAdminController.class);

    private final UserDAO userDAO;
    private final AuctionDAO auctionDAO;
    private final WalletDAO walletDAO;
    private final WithdrawalDAO withdrawalDAO;

    /**
     * Constructor đầy đủ với tất cả dependency.
     *
     * @param userDAO       DAO quản lý tài khoản người dùng.
     * @param auctionDAO    DAO quản lý phiên đấu giá.
     * @param walletDAO     DAO quản lý ví và số dư.
     * @param withdrawalDAO DAO quản lý yêu cầu rút tiền.
     */
    public ServerAdminController(UserDAO userDAO,
                                 AuctionDAO auctionDAO,
                                 WalletDAO walletDAO,
                                 WithdrawalDAO withdrawalDAO) {
        this.userDAO        = userDAO;
        this.auctionDAO     = auctionDAO;
        this.walletDAO      = walletDAO;
        this.withdrawalDAO  = withdrawalDAO;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CÁC METHOD ADMIN HIỆN CÓ (giữ nguyên)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Blocks a user account, preventing them from logging in.
     * An admin cannot block themselves or another admin account.
     */
    public boolean blockUser(Admin admin, String targetUserId) {
        if (!isAuthorizedAdmin(admin)) return false;
        if (admin.getId().equals(targetUserId)) {
            log.warn("Admin {} attempted to block themselves. Action denied.", admin.getUserName());
            return false;
        }

        try {
            User targetUser = userDAO.getUserById(targetUserId);
            if (targetUser != null && targetUser.getRole().equalsIgnoreCase("ADMIN")) {
                log.warn("Admin {} attempted to block another admin ({}). Action denied.",
                        admin.getUserName(), targetUser.getUserName());
                return false;
            }
            return userDAO.updateUserBlockStatus(targetUserId, true);
        } catch (SQLException e) {
            log.error("Database error while blocking user {}", targetUserId, e);
            return false;
        }
    }

    /** Unblocks a previously blocked user account. */
    public boolean unblockUser(Admin admin, String targetUserId) {
        if (!isAuthorizedAdmin(admin)) return false;
        try {
            return userDAO.updateUserBlockStatus(targetUserId, false);
        } catch (SQLException e) {
            log.error("Database error while unblocking user {}", targetUserId, e);
            return false;
        }
    }

    /** Permanently deletes a user account. Cannot delete another admin. */
    public boolean deleteUser(Admin admin, String targetUserId) {
        if (!isAuthorizedAdmin(admin)) return false;
        if (admin.getId().equals(targetUserId)) return false;
        try {
            User targetUser = userDAO.getUserById(targetUserId);
            if (targetUser != null && targetUser.getRole().equalsIgnoreCase("ADMIN")) return false;
            return userDAO.deleteUser(targetUserId);
        } catch (SQLException e) {
            log.error("Database error while deleting user {}", targetUserId, e);
            return false;
        }
    }

    /** Approves a pending auction, scheduling it with recalculated times if needed. */
    public boolean approveAuction(Admin admin, String auctionId) {
        if (!isAuthorizedAdmin(admin)) {
            log.warn("Unauthorized approval attempt for auction {}", auctionId);
            return false;
        }
        try {
            Auction auction = auctionDAO.getAuctionById(auctionId);
            if (auction == null || !auction.getStatus().equals(Auction.STATUS_PENDING)) {
                log.warn("Approval blocked: auction {} is not in PENDING state.", auctionId);
                return false;
            }

            LocalDateTime[] times = auctionDAO.getAuctionTimes(auctionId);
            if (times == null) return false;

            LocalDateTime now      = LocalDateTime.now();
            LocalDateTime newStart = times[0];
            LocalDateTime newEnd   = times[1];

            if (newStart == null || !newStart.isAfter(now)) {
                long durationMinutes = (newStart != null && newEnd != null)
                        ? java.time.Duration.between(newStart, newEnd).toMinutes()
                        : 60L;
                newStart = now;
                newEnd   = now.plusMinutes(durationMinutes);
                log.info("Auction {} approved: recalculated start time to NOW.", auctionId);
            } else {
                log.info("Auction {} approved: keeping original scheduled times.", auctionId);
            }

            boolean dbSuccess = auctionDAO.updateApprovalStatus(auctionId, Auction.STATUS_OPEN, newStart, newEnd);
            if (dbSuccess) {
                log.info("Admin {} approved auction {}.", admin.getUserName(), auctionId);
                Auction approvedAuction = auctionDAO.getAuctionById(auctionId);
                if (approvedAuction != null) {
                    AuctionManager.addAuctionToMonitor(approvedAuction);
                }
                return true;
            }
        } catch (SQLException e) {
            log.error("Database error approving auction {}", auctionId, e);
        }
        return false;
    }

    /** Rejects a pending auction, marking it as CANCELED. */
    public boolean rejectAuction(Admin admin, String auctionId) {
        if (!isAuthorizedAdmin(admin)) return false;
        try {
            LocalDateTime[] times = auctionDAO.getAuctionTimes(auctionId);
            if (times == null) return false;
            boolean dbSuccess = auctionDAO.updateApprovalStatus(auctionId, Auction.STATUS_CANCELED, times[0], times[1]);
            if (dbSuccess) {
                log.info("Admin {} rejected auction {}.", admin.getUserName(), auctionId);
            }
            return dbSuccess;
        } catch (SQLException e) {
            log.error("Database error rejecting auction {}", auctionId, e);
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WITHDRAWAL — Checker Step (Admin duyệt / từ chối)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lấy danh sách toàn bộ yêu cầu rút tiền đang chờ Admin xử lý (status = PENDING).
     *
     * @param admin Admin đang thực hiện yêu cầu (phải có quyền ADMIN).
     * @return Danh sách các yêu cầu PENDING kèm thông tin user; list rỗng nếu không có.
     * @throws SecurityException nếu {@code admin} không có quyền hợp lệ.
     */
    public List<Map<String, Object>> fetchPendingWithdrawals(Admin admin) {
        if (!isAuthorizedAdmin(admin)) {
            throw new SecurityException("Unauthorized: only admins can fetch withdrawal requests.");
        }
        try {
            return withdrawalDAO.getPendingRequests();
        } catch (SQLException e) {
            log.error("DB error fetching pending withdrawals", e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Xử lý một yêu cầu rút tiền theo hành động của Admin (APPROVE hoặc REJECT).
     *
     * <p><b>APPROVE — Luồng COMPLETED (2 bước ACID):</b></p>
     * <ol>
     *   <li>Trừ vĩnh viễn {@code amount} khỏi {@code locked_balance} của user
     *       (tiền rời khỏi hệ thống).</li>
     *   <li>Cập nhật trạng thái yêu cầu thành {@code COMPLETED} với Optimistic Locking
     *       ({@code WHERE status = 'PENDING'}) để ngăn xử lý 2 lần.</li>
     *   <li>Ghi lịch sử giao dịch âm vào {@code wallet_transactions} để audit.</li>
     * </ol>
     *
     * <p><b>REJECT — Luồng REJECTED (2 bước ACID):</b></p>
     * <ol>
     *   <li>Hoàn trả {@code amount} từ {@code locked_balance} về {@code balance}
     *       (user lấy lại được tiền).</li>
     *   <li>Cập nhật trạng thái yêu cầu thành {@code REJECTED}.</li>
     *   <li>Ghi lịch sử giao dịch hoàn tiền vào {@code wallet_transactions}.</li>
     * </ol>
     *
     * <p><b>⚠ QUAN TRỌNG:</b> Toàn bộ luồng APPROVE và REJECT đều được bọc trong
     * {@link TransactionManager#submitTask} và một JDBC transaction duy nhất để
     * đảm bảo tính nguyên tử — không bao giờ xảy ra tình trạng tiền bị mất.</p>
     *
     * @param admin      Admin đang thực hiện hành động.
     * @param requestId  ID của yêu cầu rút tiền cần xử lý.
     * @param isApproved {@code true} = duyệt (APPROVE), {@code false} = từ chối (REJECT).
     * @return {@link CompletableFuture} chứa một trong các kết quả:
     *         <ul>
     *           <li>{@code "SUCCESS"}      — Xử lý thành công.</li>
     *           <li>{@code "NOT_FOUND"}    — Yêu cầu không tồn tại hoặc đã được xử lý.</li>
     *           <li>{@code "WALLET_ERROR"} — Lỗi thao tác ví (vd: locked_balance không đủ).</li>
     *           <li>{@code "DB_ERROR"}     — Lỗi hệ thống database.</li>
     *         </ul>
     */
    public CompletableFuture<String> processWithdrawal(Admin admin,
                                                       String requestId,
                                                       boolean isApproved) {
        if (!isAuthorizedAdmin(admin)) {
            return CompletableFuture.completedFuture("UNAUTHORIZED");
        }

        Callable<String> task = () -> {
            String now     = LocalDateTime.now().toString();
            String adminId = admin.getId();
            String action  = isApproved ? "APPROVE" : "REJECT";

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // ── ĐỌC YÊU CẦU trong transaction để tránh phantom read ───────
                    Map<String, Object> request =
                            withdrawalDAO.getRequestByIdWithLock(conn, requestId);

                    if (request == null) {
                        conn.rollback();
                        log.warn("[WITHDRAW-{}] Request {} not found or already processed.",
                                action, requestId);
                        return "NOT_FOUND";
                    }

                    String userId = (String) request.get("userId");
                    long amount   = ((Number) request.get("amount")).longValue();

                    if (isApproved) {
                        // ── APPROVE: Trừ vĩnh viễn khỏi locked_balance ───────────
                        boolean deducted = walletDAO.deductFromLocked(conn, userId, amount);
                        if (!deducted) {
                            conn.rollback();
                            log.error("[WITHDRAW-APPROVE] Failed to deduct locked_balance "
                                    + "for user {} (amount={}). Data inconsistency!", userId, amount);
                            return "WALLET_ERROR";
                        }

                        // Ghi lịch sử giao dịch rút tiền (âm)
                        walletDAO.addTransaction(
                                conn,
                                "WD-OUT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                                userId,
                                -amount,
                                "Withdrawal approved (Request ID: " + requestId + ")",
                                now
                        );

                        // Cập nhật trạng thái → COMPLETED (Optimistic Lock)
                        boolean updated = withdrawalDAO.approveWithdrawal(conn, requestId, adminId, now);
                        if (!updated) {
                            conn.rollback();
                            log.warn("[WITHDRAW-APPROVE] Optimistic lock failed for request {}. "
                                    + "Concurrent modification detected.", requestId);
                            return "NOT_FOUND";
                        }

                        conn.commit();
                        log.info("[WITHDRAW-APPROVE] Request {} COMPLETED by admin {} | user={}, amount={}",
                                requestId, admin.getUserName(), userId, amount);

                        // Thông báo real-time cho user (nếu đang online)
                        ClientManager.sendToUser(userId, "WITHDRAW_APPROVED",
                                Map.of(
                                        "requestId", requestId,
                                        "amount",    amount,
                                        "message",   "Yêu cầu rút tiền của bạn đã được duyệt và hoàn tất."
                                ));

                    } else {
                        // ── REJECT: Hoàn tiền về balance ─────────────────────────
                        boolean unlocked = walletDAO.unlockBalance(conn, userId, amount);
                        if (!unlocked) {
                            conn.rollback();
                            log.error("[WITHDRAW-REJECT] Failed to unlock balance "
                                    + "for user {} (amount={}). Data inconsistency!", userId, amount);
                            return "WALLET_ERROR";
                        }

                        // Ghi lịch sử hoàn tiền
                        walletDAO.addTransaction(
                                conn,
                                "WD-REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                                userId,
                                amount,
                                "Withdrawal rejected — refunded (Request ID: " + requestId + ")",
                                now
                        );

                        // Cập nhật trạng thái → REJECTED (Optimistic Lock)
                        boolean updated = withdrawalDAO.rejectWithdrawal(conn, requestId, adminId, now);
                        if (!updated) {
                            conn.rollback();
                            log.warn("[WITHDRAW-REJECT] Optimistic lock failed for request {}. "
                                    + "Concurrent modification detected.", requestId);
                            return "NOT_FOUND";
                        }

                        conn.commit();
                        log.info("[WITHDRAW-REJECT] Request {} REJECTED by admin {} | user={}, refunded={}",
                                requestId, admin.getUserName(), userId, amount);

                        // Thông báo real-time cho user
                        ClientManager.sendToUser(userId, "WITHDRAW_REJECTED",
                                Map.of(
                                        "requestId", requestId,
                                        "amount",    amount,
                                        "message",   "Yêu cầu rút tiền của bạn đã bị từ chối. "
                                                + "Số tiền đã được hoàn lại vào ví."
                                ));
                    }

                    return "SUCCESS";

                } catch (SQLException e) {
                    conn.rollback();
                    log.error("[WITHDRAW-{}] SQL error processing request {}: {}",
                            action, requestId, e.getMessage(), e);
                    return "DB_ERROR";
                }
            } catch (SQLException e) {
                log.error("[WITHDRAW-{}] DB connection error for request {}: {}",
                        action, requestId, e.getMessage(), e);
                return "DB_ERROR";
            }
        };

        return TransactionManager.submitTask(task);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isAuthorizedAdmin(Admin admin) {
        return admin != null && admin.getRole().equalsIgnoreCase("ADMIN");
    }
}