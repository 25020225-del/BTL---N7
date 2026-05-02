package controller;

<<<<<<< HEAD
import database.DatabaseManager;
import model.user.Admin;
import model.user.User;
import model.auction.Auction;
=======
import model.user.Admin;
import model.user.User;
import model.auction.Auction;
>>>>>>> df73b5cfd21e32839620dec3b4e4f4bde75eecf1

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

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

        String sql = "UPDATE auctions SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, Auction.STATUS_OPEN);
            pstmt.setString(2, auction.getId());

            if (pstmt.executeUpdate() > 0) {
                auction.setStatus(Auction.STATUS_OPEN);
                System.out.println("[System]: Admin \"" + YELLOW + admin.getName() + RESET + "\" has approved auction \"" + YELLOW + auction.getId() + RESET + "\"");
                return true;
            }
        } catch (SQLException e) {
            System.out.println("[Error]: DB Error during approval: " + RED + e.getMessage() + RESET);
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
<<<<<<< HEAD
            String sql = "UPDATE users SET is_good = 1 WHERE id = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, user.getId());
                if (pstmt.executeUpdate() > 0) {
                    user.setGood(true);
                    System.out.println("[System]: Admin \"" + YELLOW + admin.getName() + RESET + "\" has verified Seller \"" + YELLOW + seller.getName() + RESET + "\" as reputable");
                }
            } catch (SQLException e) {
                System.out.println("[Error]: DB Error verifying seller: " + RED + e.getMessage() + RESET);
            }
=======
            user.setGood(true);
            System.out.println("[System]: Admin \"" + YELLOW + admin.getName() + RESET + "\" has verified User \"" + YELLOW + user.getName() + RESET + "\" as reputable");
>>>>>>> df73b5cfd21e32839620dec3b4e4f4bde75eecf1
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
            String sql = "UPDATE auctions SET status = ? WHERE id = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, Auction.STATUS_CANCELED);
                pstmt.setString(2, auction.getId());

                if (pstmt.executeUpdate() > 0) {
                    auction.setStatus(Auction.STATUS_CANCELED);
                    System.out.println("[System]: Admin \"" + YELLOW + admin.getName() + RESET + "\" has rejected the auction request for \"" + YELLOW + auction.getId() + RESET + "\"");
                }
            } catch (SQLException e) {
                System.out.println("[Error]: DB Error rejecting auction: " + RED + e.getMessage() + RESET);
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
            String sql = "UPDATE auctions SET status = ? WHERE id = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, Auction.STATUS_DELETED);
                pstmt.setString(2, auction.getId());

                if (pstmt.executeUpdate() > 0) {
                    auction.setStatus(Auction.STATUS_DELETED);
                    System.out.println("[System]: Admin \"" + YELLOW + admin.getName() + RESET + "\" has permanently deleted auction \"" + YELLOW + auction.getId() + RESET + "\"");
                }
            } catch (SQLException e) {
                System.out.println("[Error]: DB Error deleting auction: " + RED + e.getMessage() + RESET);
            }
        }
    }
}