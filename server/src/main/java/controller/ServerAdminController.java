package controller;

import database.dao.AuctionDAO;
import database.dao.UserDAO;
import model.auction.Auction;
import model.user.Admin;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;

import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Controller responsible for handling administrative actions on the server side.
 */
public class ServerAdminController {

    private static final Logger log = LoggerFactory.getLogger(ServerAdminController.class);
    private final UserDAO userDAO;
    private final AuctionDAO auctionDAO;

    public ServerAdminController(UserDAO userDAO, AuctionDAO auctionDAO) {
        this.userDAO    = userDAO;
        this.auctionDAO = auctionDAO;
    }

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

            boolean success = userDAO.updateUserBlockStatus(targetUserId, true);
            if (success) {
                log.info("Admin {} blocked user {}", admin.getUserName(), targetUserId);
                ClientManager.kickTargetById(targetUserId, "Tài khoản của bạn đã bị khóa bởi Quản trị viên.");
            }
            return success;

        } catch (SQLException e) {
            log.error("Database error while blocking user {}", targetUserId, e);
            return false;
        }
    }

    /**
     * Unblocks a previously blocked user account.
     */
    public boolean unblockUser(Admin admin, String targetUserId) {
        if (!isAuthorizedAdmin(admin)) return false;
        if (admin.getId().equals(targetUserId)) return false;

        try {
            boolean success = userDAO.updateUserBlockStatus(targetUserId, false);
            if (success) {
                log.info("Admin {} unblocked user {}", admin.getUserName(), targetUserId);
            }
            return success;
        } catch (SQLException e) {
            log.error("Database error while unblocking user {}", targetUserId, e);
            return false;
        }
    }

    /**
     * Permanently deletes a user account. Cannot delete another admin.
     */
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

    /**
     * Approves a pending auction, scheduling it with recalculated times if needed.
     */
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

    /**
     * Rejects a pending auction, marking it as CANCELED.
     */
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

    // ─────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────

    private boolean isAuthorizedAdmin(Admin admin) {
        return admin != null && admin.getRole().equalsIgnoreCase("ADMIN");
    }
}