package controller;

import database.DatabaseManager;
import model.Admin;
import model.Seller;
import model.Auction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static utils.ConsoleColors.*;

public class ServerAdminController {

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

    public void verifySeller(Admin admin, Seller seller) {
        if (admin != null && admin.getRole().equalsIgnoreCase("ADMIN")) {
            String sql = "UPDATE users SET is_good = 1 WHERE id = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, seller.getId());
                if (pstmt.executeUpdate() > 0) {
                    seller.setGood(true);
                    System.out.println("[System]: Admin \"" + YELLOW + admin.getName() + RESET + "\" has verified Seller \"" + YELLOW + seller.getName() + RESET + "\" as reputable");
                }
            } catch (SQLException e) {
                System.out.println("[Error]: DB Error verifying seller: " + RED + e.getMessage() + RESET);
            }
        }
    }

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