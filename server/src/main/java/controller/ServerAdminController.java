package controller;

import model.auction.Auction;
import model.user.Admin;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ServerExtension.ClientManager;

/**
 * Controller responsible for handling administrative actions on the server side.
 * It provides methods for managing auction lifecycles (approval, rejection, deletion)
 * and verifying user trust levels.
 */
public class ServerAdminController {
    private static final Logger log = LoggerFactory.getLogger(ServerAdminController.class);
    private final database.dao.UserDAO userDAO;
    private final database.dao.AuctionDAO auctionDAO;

    public ServerAdminController(database.dao.UserDAO userDAO, database.dao.AuctionDAO auctionDAO) {
        this.userDAO = userDAO;
        this.auctionDAO = auctionDAO;
    }

    /**
     * Blocks a user from participating in auctions với cơ chế bảo vệ Admin.
     *
     * @param admin        The administrator performing the action.
     * @param targetUserId The ID of the user to be blocked.
     * @return {@code true} if successful.
     */
    public boolean blockUser(Admin admin, String targetUserId) {
        if (admin == null || !admin.getRole().equalsIgnoreCase("ADMIN")) return false;

        // 1. LỚP BẢO VỆ 1: Chống tự khóa chính mình
        if (admin.getId().equals(targetUserId)) {
            log.warn("Admin {} cố gắng tự khóa chính mình. Hành động bị từ chối.", admin.getUserName());
            return false;
        }

        try {
            // 2. LỚP BẢO VỆ 2: Chống khóa các Admin khác
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

    /**
     * Unblocks a previously blocked user (Chỉ áp dụng cho USER).
     *
     * @param admin        The administrator performing the action.
     * @param targetUserId The ID of the user to be unblocked.
     * @return {@code true} if successful.
     */
    public boolean unblockUser(Admin admin, String targetUserId) {
        if (admin == null || !admin.getRole().equalsIgnoreCase("ADMIN")) return false;

        // Admin không cần unblock chính mình hoặc admin khác vì họ vốn không được phép bị block
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

    /**
     * Xóa người dùng khỏi hệ thống.
     */
    public boolean deleteUser(Admin admin, String targetUserId) {
        if (admin == null || !admin.getRole().equalsIgnoreCase("ADMIN")) return false;

        // Chống tự xóa chính mình
        if (admin.getId().equals(targetUserId)) return false;

        try {
            User targetUser = userDAO.getUserById(targetUserId);
            // Chống xóa Admin khác
            if (targetUser != null && targetUser.getRole().equalsIgnoreCase("ADMIN")) return false;

            return userDAO.deleteUser(targetUserId);
        } catch (Exception e) {
            log.error("Lỗi khi xóa user: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Approves a pending auction request, transitioning its status to OPEN.
     */
    public boolean approveAuction(Admin admin, Auction auction) {
        if (admin == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
            log.warn("User does not have approval rights.");
            return false;
        }

        try {
            boolean dbSuccess = auctionDAO.updateAuctionStatus(auction.getId(), Auction.STATUS_OPEN);
            if (dbSuccess) {
                auction.setStatus(Auction.STATUS_OPEN);
                log.info("{} has approved auction {}.", admin.getUserName(), auction.getId());
                return true;
            } else {
                log.error("Failed to update auction status in database for auction {}", auction.getId());
            }
        } catch (Exception e) {
            log.error("Error approving auction {}: {}", auction.getId(), e.getMessage());
        }

        return false;
    }

    /**
     * Verifies a user as a trusted/reputable seller.
     */
    public void verifySeller(Admin admin, User user) {
        if (admin != null && admin.getRole().equalsIgnoreCase("ADMIN")) {
            try {
                user.setGood(true);
                userDAO.updateUserTrustLevel(user.getId(), true);
                log.info("{} has verified {} as reputable", admin.getUserName(), user.getUserName());
            } catch (Exception e) {
                log.error("Error verifying seller {}: {}", user.getId(), e.getMessage());
            }
        }
    }

    /**
     * Rejects a pending auction request, setting its status to CANCELED.
     */
    public void rejectAuctionRequest(Admin admin, Auction auction) {
        if (admin != null && admin.getRole().equalsIgnoreCase("ADMIN")) {
            try {
                boolean dbSuccess = auctionDAO.updateAuctionStatus(auction.getId(), Auction.STATUS_CANCELED);
                if (dbSuccess) {
                    auction.setStatus(Auction.STATUS_CANCELED);
                    log.info("{} has rejected the auction request for {}.", admin.getUserName(), auction.getId());
                }
            } catch (Exception e) {
                log.error("Error rejecting auction {}: {}", auction.getId(), e.getMessage());
            }
        }
    }

    /**
     * Forcibly and permanently deletes an auction session from the active system.
     */
    public void forceDeleteAuction(Admin admin, Auction auction) {
        if (admin != null && admin.getRole().equalsIgnoreCase("ADMIN")) {
            try {
                boolean dbSuccess = auctionDAO.updateAuctionStatus(auction.getId(), Auction.STATUS_DELETED);
                if (dbSuccess) {
                    auction.setStatus(Auction.STATUS_DELETED);
                    log.info("{} has deleted auction {}.", admin.getUserName(), auction.getId());
                }
            } catch (Exception e) {
                log.error("Error force deleting auction {}: {}", auction.getId(), e.getMessage());
            }
        }
    }
}