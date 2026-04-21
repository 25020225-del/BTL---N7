package controller;

import database.DatabaseManager;
import model.Auction;
import model.Item;
import model.Seller;
import model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static utils.ConsoleColors.*;

public class ServerSellerController {

    public Auction addAuction(User currentUser, Item item, double bidIncrement, int durationMinutes) {
        Seller seller = new Seller(currentUser);

        Auction newAuction = Auction.createNewAuction(item, seller, bidIncrement, durationMinutes);

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
            pstmt.setString(10, seller.getId());

            pstmt.executeUpdate();
            System.out.println("[System]: Seller \"" + YELLOW + seller.getName() + RESET + "\" created auction: " + item.getItemName());

        } catch (SQLException e) {
            System.out.println("[Error]: Database error during addAuction: " + utils.ConsoleColors.RED + e.getMessage() + utils.ConsoleColors.RESET);
            return null;
        }

        return newAuction;
    }

    public boolean editAuction(User currentUser, Auction auction, String newName, String newDesc, double newStartPrice, LocalDateTime newStartTime, LocalDateTime newEndTime) {
        if (!auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("[Security]: " + RED + "You are not the owner of this auction" + RESET);
            return false;
        }

        if (auction.getStatus().equals(Auction.STATUS_RUNNING) ||
                auction.getStatus().equals(Auction.STATUS_FINISHED) ||
                auction.getStatus().equals(Auction.STATUS_DELETED)) {
            System.out.println("[Error]: " + RED + "Cannot edit information while the auction is ongoing, finished, or deleted" + RESET);
            return false;
        }

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

    public boolean deleteAuction(User currentUser, Auction auction) {
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