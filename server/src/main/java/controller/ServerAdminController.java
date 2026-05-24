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
 * Controller handling administrative control operations including account enforcement,
 * auction approval pipelines, and financial withdrawal validations (Maker-Checker verification).
 */
public class ServerAdminController {

    private static final Logger log = LoggerFactory.getLogger(ServerAdminController.class);
    private final UserDAO userDAO;
    private final AuctionDAO auctionDAO;
    private final WalletDAO walletDAO;
    private final WithdrawalDAO withdrawalDAO;

    public ServerAdminController(UserDAO userDAO, AuctionDAO auctionDAO, WalletDAO walletDAO, WithdrawalDAO withdrawalDAO) {
        this.userDAO = userDAO;
        this.auctionDAO = auctionDAO;
        this.walletDAO = walletDAO;
        this.withdrawalDAO = withdrawalDAO;
    }

    public boolean blockUser(Admin admin, String targetUserId) {
        if (!isAuthorizedAdmin(admin)) return false;
        if (admin.getId().equals(targetUserId)) {
            log.warn("Admin {} attempted to block themselves. Action denied.", admin.getUserName());
            return false;
        }

        try {
            User targetUser = userDAO.getUserById(targetUserId);
            if (targetUser != null && targetUser.getRole().equalsIgnoreCase("ADMIN")) {
                log.warn("Admin {} attempted to block another admin ({}). Action denied.", admin.getUserName(), targetUser.getUserName());
                return false;
            }
            return userDAO.updateUserBlockStatus(targetUserId, true);
        } catch (SQLException e) {
            log.error("Database error while blocking user {}", targetUserId, e);
            return false;
        }
    }

    public boolean unblockUser(Admin admin, String targetUserId) {
        if (!isAuthorizedAdmin(admin)) return false;
        try {
            return userDAO.updateUserBlockStatus(targetUserId, false);
        } catch (SQLException e) {
            log.error("Database error while unblocking user {}", targetUserId, e);
            return false;
        }
    }

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

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime newStart = times[0];
            LocalDateTime newEnd = times[1];

            if (newStart == null || !newStart.isAfter(now)) {
                long durationMinutes = (newStart != null && newEnd != null)
                        ? java.time.Duration.between(newStart, newEnd).toMinutes()
                        : 60L;
                newStart = now;
                newEnd = now.plusMinutes(durationMinutes);
                log.info("Auction {} approved: recalculated start time to NOW.", auctionId);
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

    public boolean rejectAuction(Admin admin, String auctionId) {
        if (!isAuthorizedAdmin(admin)) return false;
        try {
            LocalDateTime[] times = auctionDAO.getAuctionTimes(auctionId);
            if (times == null) return false;
            return auctionDAO.updateApprovalStatus(auctionId, Auction.STATUS_CANCELED, times[0], times[1]);
        } catch (SQLException e) {
            log.error("Database error rejecting auction {}", auctionId, e);
            return false;
        }
    }

    public ToggleResult toggleGoodStatus(Admin admin, String targetUserId) {
        if (!isAuthorizedAdmin(admin)) {
            log.warn("[TOGGLE_GOOD] Unauthorized attempt by user '{}'.", admin.getUserName());
            return ToggleResult.unauthorized();
        }
        if (admin.getId().equals(targetUserId)) {
            log.warn("[TOGGLE_GOOD] Admin '{}' attempted to toggle their own status. Denied.", admin.getUserName());
            return ToggleResult.denied("Admin không thể thay đổi trạng thái của chính mình.");
        }

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                User target = userDAO.getUserById(targetUserId);
                if (target == null) {
                    conn.rollback();
                    return ToggleResult.notFound();
                }
                if (target.getRole().equalsIgnoreCase("ADMIN")) {
                    conn.rollback();
                    return ToggleResult.denied("Không thể thay đổi trạng thái Trusted của tài khoản Admin.");
                }

                if (!userDAO.toggleGoodStatus(conn, targetUserId)) {
                    conn.rollback();
                    return ToggleResult.dbError();
                }

                Boolean newStatus = userDAO.readGoodStatus(conn, targetUserId);
                if (newStatus == null) {
                    conn.rollback();
                    return ToggleResult.dbError();
                }

                conn.commit();
                log.info("[TOGGLE_GOOD] Admin '{}' toggled user '{}' → is_good = {}.", admin.getUserName(), targetUserId, newStatus);
                return ToggleResult.success(targetUserId, newStatus);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            log.error("[TOGGLE_GOOD] Database error while toggling user '{}': {}", targetUserId, e.getMessage(), e);
            return ToggleResult.dbError();
        }
    }

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

    public CompletableFuture<String> processWithdrawal(Admin admin, String requestId, boolean isApproved) {
        if (!isAuthorizedAdmin(admin)) {
            return CompletableFuture.completedFuture("UNAUTHORIZED");
        }

        Callable<String> task = () -> {
            String now = LocalDateTime.now().toString();
            String adminId = admin.getId();
            String action = isApproved ? "APPROVE" : "REJECT";

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    Map<String, Object> request = withdrawalDAO.getRequestByIdWithLock(conn, requestId);
                    if (request == null) {
                        conn.rollback();
                        return "NOT_FOUND";
                    }

                    String userId = (String) request.get("userId");
                    long amount = ((Number) request.get("amount")).longValue();

                    if (isApproved) {
                        if (!walletDAO.deductFromLocked(conn, userId, amount)) {
                            conn.rollback();
                            return "WALLET_ERROR";
                        }

                        walletDAO.addTransaction(conn, "WD-OUT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                                userId, -amount, "Withdrawal approved (Request ID: " + requestId + ")", now);

                        if (!withdrawalDAO.approveWithdrawal(conn, requestId, adminId, now)) {
                            conn.rollback();
                            return "NOT_FOUND";
                        }

                        conn.commit();
                        ClientManager.sendToUser(userId, "WITHDRAW_APPROVED", Map.of("requestId", requestId, "amount", amount, "message", "Yêu cầu rút tiền của bạn đã được duyệt và hoàn tất."));
                    } else {
                        if (!walletDAO.unlockBalance(conn, userId, amount)) {
                            conn.rollback();
                            return "WALLET_ERROR";
                        }

                        walletDAO.addTransaction(conn, "WD-REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                                userId, amount, "Withdrawal rejected — refunded (Request ID: " + requestId + ")", now);

                        if (!withdrawalDAO.rejectWithdrawal(conn, requestId, adminId, now)) {
                            conn.rollback();
                            return "NOT_FOUND";
                        }

                        conn.commit();
                        ClientManager.sendToUser(userId, "WITHDRAW_REJECTED", Map.of("requestId", requestId, "amount", amount, "message", "Yêu cầu rút tiền của bạn đã bị từ chối. Số tiền đã được hoàn lại vào ví."));
                    }
                    return "SUCCESS";

                } catch (SQLException e) {
                    conn.rollback();
                    log.error("[WITHDRAW-{}] SQL error processing request {}: {}", action, requestId, e.getMessage(), e);
                    return "DB_ERROR";
                }
            } catch (SQLException e) {
                log.error("[WITHDRAW-{}] DB connection error for request {}: {}", action, requestId, e.getMessage(), e);
                return "DB_ERROR";
            }
        };

        return TransactionManager.submitTask(task);
    }

    private boolean isAuthorizedAdmin(Admin admin) {
        return admin != null && admin.getRole().equalsIgnoreCase("ADMIN");
    }

    public static final class ToggleResult {
        public enum Status {SUCCESS, UNAUTHORIZED, DENIED, NOT_FOUND, DB_ERROR}

        private final Status status;
        private final String userId;
        private final Boolean newIsGood;
        private final String message;

        private ToggleResult(Status status, String userId, Boolean newIsGood, String message) {
            this.status = status;
            this.userId = userId;
            this.newIsGood = newIsGood;
            this.message = message;
        }

        public static ToggleResult success(String userId, boolean newIsGood) {
            String label = newIsGood ? "Trusted (ưu tiên duyệt tự động)" : "Thường (chờ Admin duyệt)";
            return new ToggleResult(Status.SUCCESS, userId, newIsGood, "Đã cập nhật trạng thái người dùng thành: " + label);
        }

        public static ToggleResult unauthorized() { return new ToggleResult(Status.UNAUTHORIZED, null, null, "Bạn không có quyền thực hiện thao tác này."); }
        public static ToggleResult denied(String reason) { return new ToggleResult(Status.DENIED, null, null, reason); }
        public static ToggleResult notFound() { return new ToggleResult(Status.NOT_FOUND, null, null, "Không tìm thấy người dùng với ID đã cung cấp."); }
        public static ToggleResult dbError() { return new ToggleResult(Status.DB_ERROR, null, null, "Lỗi cơ sở dữ liệu khi cập nhật trạng thái người dùng."); }

        public Status getStatus() { return status; }
        public String getUserId() { return userId; }
        public Boolean getNewIsGood() { return newIsGood; }
        public String getMessage() { return message; }
        public boolean isSuccess() { return status == Status.SUCCESS; }
    }
}