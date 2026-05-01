package controller;

import database.DatabaseManager;
import model.auction.Auction;
import model.item.Item;
import model.user.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static utils.ConsoleColors.*;

/**
 * Controller responsible for handling auction-related actions initiated by a seller.
 * It provides functionality to create new auctions, modify existing ones under specific
 * conditions, and handle the deletion/removal of auction sessions from the active database.
 */
public class ServerSellerController {

    /**
     * Creates and persists a new auction session in the database.
     * This method generates a dynamic auction ID and calculates the end time based
     * on the provided duration.
     *
     * @param currentUser     The authenticated user who is hosting/selling the item.
     * @param item            The item entity to be placed under auction.
     * @param bidIncrement    The minimum amount that each subsequent bid must increase by.
     * @param durationMinutes The total time the auction will remain active.
     * @return A newly created {@link Auction} instance if successful; {@code null} if a database error occurs.
     */
    public Auction addAuction(User currentUser, Item item, double bidIncrement, int durationMinutes) {
        // Utilize the factory method to prepare the Auction object in RAM
        Auction newAuction = Auction.createNewAuction(item, currentUser, bidIncrement, durationMinutes);

        String sql = "INSERT INTO auctions (id, item_name, description, starting_price, current_price, bid_increment, start_time, end_time, status, seller_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newAuction.getId());
            pstmt.setString(2, item.getItemName());
            pstmt.setString(3, item.getDescription());
            pstmt.setDouble(4, item.getStartingPrice());
            pstmt.setDouble(5, newAuction.getCurrentPrice());
            pstmt.setDouble(6, bidIncrement);
            pstmt.setString(7, newAuction.getStartTime().toString());
            pstmt.setString(8, newAuction.getEndTime().toString());
            pstmt.setString(9, newAuction.getStatus());
            pstmt.setString(10, currentUser.getId());

            pstmt.executeUpdate();
            System.out.println("[System]: User \"" + YELLOW + currentUser.getName() + RESET + "\" created auction: " + item.getItemName());

        } catch (SQLException e) {
            System.out.println("[Error]: Database error during addAuction: " + utils.ConsoleColors.RED + e.getMessage() + utils.ConsoleColors.RESET);
            return null;
        }

        return newAuction;
    }

    /**
     * Updates the information of an existing auction.
     * Modification is strictly prohibited if the auction is already RUNNING,
     * FINISHED, or DELETED to maintain system integrity.
     *
     * @param currentUser   The user attempting the edit (must be the original seller).
     * @param auction       The auction session to be modified.
     * @param newName       The updated item name.
     * @param newDesc       The updated item description.
     * @param newStartPrice The updated starting/base price.
     * @param newStartTime  The updated scheduled start time.
     * @param newEndTime    The updated scheduled end time.
     * @return {@code true} if the update was successful and permitted; {@code false} otherwise.
     */
    public boolean editAuction(User currentUser, Auction auction, String newName, String newDesc, double newStartPrice, LocalDateTime newStartTime, LocalDateTime newEndTime) {
        // Security check: Only the owner can edit the auction
        if (!auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("[Security]: " + RED + "You are not the owner of this auction" + RESET);
            return false;
        }

        // Logic check: Cannot edit auctions that have already started or concluded
        if (auction.getStatus().equals(Auction.STATUS_RUNNING) ||
                auction.getStatus().equals(Auction.STATUS_FINISHED) ||
                auction.getStatus().equals(Auction.STATUS_DELETED)) {
            System.out.println("[Error]: " + RED + "Cannot edit information while the auction is ongoing, finished, or deleted" + RESET);
            return false;
        }

        // If an auction was previously canceled, editing it resets it to PENDING for re-approval
        String newStatus = auction.getStatus().equals(Auction.STATUS_CANCELED) ? Auction.STATUS_PENDING : auction.getStatus();
        String sql = "UPDATE auctions SET item_name = ?, description = ?, starting_price = ?, current_price = ?, start_time = ?, end_time = ?, status = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newName);
            pstmt.setString(2, newDesc);
            pstmt.setDouble(3, newStartPrice);
            pstmt.setDouble(4, newStartPrice);
            pstmt.setString(5, newStartTime.toString());
            pstmt.setString(6, newEndTime.toString());
            pstmt.setString(7, newStatus);
            pstmt.setString(8, auction.getId());

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                // Synchronize the RAM object with the Database updates
                auction.getItem().setItemName(newName);
                auction.getItem().setDescription(newDesc);
                auction.getItem().setStartingPrice(newStartPrice);
                auction.setCurrentPrice(newStartPrice);
                auction.setStartTime(newStartTime);
                auction.setEndTime(newEndTime);
                auction.setStatus(newStatus);

                System.out.println("[System]: Auction \"" + YELLOW + auction.getId() + RESET + "\" updated successfully");
                return true;
            }
        } catch (SQLException e) {
            System.out.println("[Error]: Database error during editAuction: " + RED + e.getMessage() + RESET);
        }
        return false;
    }

    /**
     * Marks an auction as DELETED in the system.
     * This method verifies ownership before performing the status transition.
     *
     * @param currentUser The user attempting the deletion.
     * @param auction     The auction session to be removed.
     * @return {@code true} if the deletion was successful; {@code false} if unauthorized or a database error occurred.
     */
    public boolean deleteAuction(User currentUser, Auction auction) {
        // Security check: Only the owner can delete the auction
        if (!auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("[Security]: " + RED + "You do not have permission to delete this product" + RESET);
            return false;
        }

        String sql = "UPDATE auctions SET status = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, Auction.STATUS_DELETED);
            pstmt.setString(2, auction.getId());

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                auction.setStatus(Auction.STATUS_DELETED);
                System.out.println("[System]: Auction \"" + YELLOW + auction.getId() + RESET + "\" has been deleted by " + YELLOW + currentUser.getName() + RESET);
                return true;
            }
        } catch (SQLException e) {
            System.out.println("[Error]: Database error during deleteAuction: " + RED + e.getMessage() + RESET);
        }
        return false;
    }
}