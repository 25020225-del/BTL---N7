package controller;

import model.auction.Auction;
import model.user.Admin;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * Blocks a user from participating in auctions.
     *
     * @param admin  The administrator performing the action.
     * @param userId The ID of the user to be blocked.
     * @return {@code true} if successful.
     */
    public boolean blockUser(Admin admin, String userId) {
        if (admin != null && admin.getRole().equalsIgnoreCase("ADMIN")) {
            try {
                boolean success = userDAO.updateUserBlockStatus(userId, true);
                if (success) {
                    log.info("Admin {} blocked user {}", admin.getUserName(), userId);
                    return true;
                }
            } catch (Exception e) {
                log.error("Error blocking user: {}", e.getMessage());
            }
        }
        return false;
    }

    /**
     * Unblocks a previously blocked user.
     *
     * @param admin  The administrator performing the action.
     * @param userId The ID of the user to be unblocked.
     * @return {@code true} if successful.
     */
    public boolean unblockUser(Admin admin, String userId) {
        if (admin != null && admin.getRole().equalsIgnoreCase("ADMIN")) {
            try {
                boolean success = userDAO.updateUserBlockStatus(userId, false);
                if (success) {
                    log.info("Admin {} unblocked user {}", admin.getUserName(), userId);
                    return true;
                }
            } catch (Exception e) {
                log.error("Error unblocking user: {}", e.getMessage());
            }
        }
        return false;
    }

    /**
     * Approves a pending auction request, transitioning its status to OPEN.
     *
     * @param admin   The administrator performing the action.
     * @param auction The auction session to be approved.
     * @return {@code true} if the approval was successful; {@code false} if the user lacks permissions.
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
     * Trusted users may have fewer restrictions when creating future auctions.
     *
     * @param admin The administrator performing the verification.
     * @param user  The target user to be marked as verified.
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
     *
     * @param admin   The administrator performing the action.
     * @param auction The auction session to be rejected.
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
     * This transitions the auction status to DELETED.
     *
     * @param admin   The administrator performing the action.
     * @param auction The auction session to be forcibly removed.
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