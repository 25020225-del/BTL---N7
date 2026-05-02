package controller;

import model.user.Admin;
import model.user.User;
import model.auction.Auction;

import static utils.ConsoleColors.*;

/**
 * Controller responsible for handling administrative actions on the server side.
 * It provides methods for managing auction lifecycles (approval, rejection, deletion)
 * and verifying user trust levels.
 */
public class ServerAdminController {

    /**
     * Approves a pending auction request, transitioning its status to OPEN.
     *
     * @param admin   The administrator performing the action.
     * @param auction The auction session to be approved.
     * @return {@code true} if the approval was successful; {@code false} if the user lacks permissions.
     */
    public boolean approveAuction(Admin admin, Auction auction) {
        if (admin == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
            System.out.println("[Security]: " + RED + "User does not have approval rights" + RESET);
            return false;
        }

        auction.setStatus(Auction.STATUS_OPEN);
        System.out.println("[System]: Admin \"" + YELLOW + admin.getName() + RESET + "\" has approved auction \"" + YELLOW + auction.getId() + RESET + "\"");

        return true;
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
            user.setGood(true);
            System.out.println("[System]: Admin \"" + YELLOW + admin.getName() + RESET + "\" has verified User \"" + YELLOW + user.getName() + RESET + "\" as reputable");
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
            auction.setStatus(Auction.STATUS_CANCELED);
            System.out.println("[System]: Admin \"" + YELLOW + admin.getName() + RESET + "\" has rejected the auction request for \"" + YELLOW + auction.getId() + RESET + "\"");
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
            auction.setStatus(Auction.STATUS_DELETED);
            System.out.println("[System]: Admin \"" + YELLOW + admin.getName() + RESET + "\" has permanently deleted auction \"" + YELLOW + auction.getId() + RESET + "\"");
        }
    }
}