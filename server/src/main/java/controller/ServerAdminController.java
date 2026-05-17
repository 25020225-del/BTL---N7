package controller;

import model.auction.Auction;
import model.user.Admin;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;
import java.time.LocalDateTime;

/**
 * Controller responsible for handling administrative actions on the server side.
 */
public class ServerAdminController {
    private static final Logger log = LoggerFactory.getLogger(ServerAdminController.class);
    private final database.dao.UserDAO userDAO;
    private final database.dao.AuctionDAO auctionDAO;

    public ServerAdminController(database.dao.UserDAO userDAO, database.dao.AuctionDAO auctionDAO) {
        this.userDAO = userDAO;
        this.auctionDAO = auctionDAO;
    }

    public boolean blockUser(Admin admin, String targetUserId) {
        if (admin == null || !admin.getRole().equalsIgnoreCase("ADMIN")) return false;

        if (admin.getId().equals(targetUserId)) {
            log.warn("Admin {} cố gắng tự khóa chính mình. Hành động bị từ chối.", admin.getUserName());
            return false;
        }

        try {
            User targetUser = userDAO.getUserById(targetUserId);
            if (targetUser != null && targetUser.getRole().equalsIgnoreCase("ADMIN")) {
                log.warn("Admin {} cố gắng khóa một Admin khác ({}). Hành động bị từ chối.",
                        admin.getUserName(), targetUser.getUserName());
                return false;
            }

            boolean success = userDAO.updateUserBlockStatus(targetUserId, true);
            if (success) {
                log.info("Admin {} đã block user {}", admin.getUserName(), targetUserId);
                ClientManager.kickTargetById(targetUserId, "Tài khoản của bạn đã bị khóa bởi Quản trị viên.");
                return true;
            }
        } catch (Exception e) {
            log.error("Lỗi khi block user: {}", e.getMessage());
        }
        return false;
    }

    public boolean unblockUser(Admin admin, String targetUserId) {
        if (admin == null || !admin.getRole().equalsIgnoreCase("ADMIN")) return false;
        if (admin.getId().equals(targetUserId)) return false;

        try {
            boolean success = userDAO.updateUserBlockStatus(targetUserId, false);
            if (success) {
                log.info("Admin {} đã unblock user {}", admin.getUserName(), targetUserId);
                return true;
            }
        } catch (Exception e) {
            log.error("Lỗi khi unblock user: {}", e.getMessage());
        }
        return false;
    }

    public boolean deleteUser(Admin admin, String targetUserId) {
        if (admin == null || !admin.getRole().equalsIgnoreCase("ADMIN")) return false;
        if (admin.getId().equals(targetUserId)) return false;

        try {
            User targetUser = userDAO.getUserById(targetUserId);
            if (targetUser != null && targetUser.getRole().equalsIgnoreCase("ADMIN")) return false;
            return userDAO.deleteUser(targetUserId);
        } catch (Exception e) {
            log.error("Lỗi khi xóa user: {}", e.getMessage());
            return false;
        }
    }

    public boolean approveAuction(Admin admin, String auctionId) {
        if (admin == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
            log.warn("User does not have approval rights.");
            return false;
        }

        try {
            Auction auction = auctionDAO.getAuctionById(auctionId);

            // [ARCHITECT FIX]: Bỏ STATUS_PENDING_APPROVAL vì không tồn tại
            if (auction == null || !auction.getStatus().equals(Auction.STATUS_PENDING)) {
                log.warn("Chặn đứng hành vi duyệt đè: Phiên đấu giá {} không ở trạng thái chờ duyệt.", auctionId);
                return false;
            }

            LocalDateTime[] times = auctionDAO.getAuctionTimes(auctionId);
            if (times == null) return false;

            LocalDateTime oldStart = times[0];
            LocalDateTime oldEnd = times[1];
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime newStart = oldStart;
            LocalDateTime newEnd = oldEnd;

            if (oldStart == null || oldStart.isBefore(now) || oldStart.isEqual(now)) {
                long duration = 60;
                if (oldStart != null && oldEnd != null) {
                    duration = java.time.Duration.between(oldStart, oldEnd).toMinutes();
                }
                newStart = now;
                newEnd = now.plusMinutes(duration);
                log.info("Admin approved late or immediate start. Recalculated new start time to NOW.");
            } else {
                log.info("Admin approved early for a future scheduled auction. Kept original times.");
            }

            if (newStart == null) newStart = now;
            if (newEnd == null) newEnd = now.plusMinutes(60);

            boolean dbSuccess = auctionDAO.updateApprovalStatus(auctionId, Auction.STATUS_OPEN, newStart, newEnd);
            if (dbSuccess) {
                log.info("{} has approved auction {}.", admin.getUserName(), auctionId);

                // [ARCHITECT FIX]: Bỏ từ khóa "Auction" ở đây để tái sử dụng biến phía trên
                auction = auctionDAO.getAuctionById(auctionId);
                if (auction != null) {
                    AuctionManager.addAuctionToMonitor(auction);
                    log.info("Auction {} added to RAM monitor after Admin approval.", auctionId);
                }
                return true;
            }
        } catch (Exception e) {
            log.error("Error approving auction {}: {}", auctionId, e.getMessage());
        }

        return false;
    }

    public boolean rejectAuction(Admin admin, String auctionId) {
        if (admin == null || !admin.getRole().equalsIgnoreCase("ADMIN")) return false;
        try {
            LocalDateTime[] times = auctionDAO.getAuctionTimes(auctionId);
            if (times == null) return false;

            boolean dbSuccess = auctionDAO.updateApprovalStatus(auctionId, Auction.STATUS_CANCELED, times[0], times[1]);
            if (dbSuccess) {
                log.info("{} has rejected the auction request for {}.", admin.getUserName(), auctionId);
                return true;
            }
        } catch (Exception e) {
            log.error("Error rejecting auction {}: {}", auctionId, e.getMessage());
        }
        return false;
    }
}