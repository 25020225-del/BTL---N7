package database.dao;

import database.DatabaseManager;
import model.auction.Auction;
import model.item.Item;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class AuctionDAO {

    public boolean addAuction(Auction auction) throws SQLException {
        String sql = "INSERT INTO auctions (id, item_name, description, starting_price, current_price, bid_increment, start_time, end_time, status, seller_id, image_url) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            Item item = auction.getItem();
            pstmt.setString(1, auction.getId());
            pstmt.setString(2, item.getItemName());
            pstmt.setString(3, item.getDescription());
            pstmt.setDouble(4, item.getStartingPrice());
            pstmt.setDouble(5, auction.getCurrentPrice());
            pstmt.setDouble(6, auction.getBidIncrement());
            pstmt.setString(7, auction.getStartTime().toString());
            pstmt.setString(8, auction.getEndTime().toString());
            pstmt.setString(9, auction.getStatus());
            pstmt.setString(10, auction.getSeller().getId());
            pstmt.setString(11, item.getImageUrl());

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean updateAuction(Auction auction, String newName, String newDesc, double newStartPrice, LocalDateTime newStartTime, LocalDateTime newEndTime, String newStatus) throws SQLException {
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

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean updateAuctionStatus(String auctionId, String status) throws SQLException {
        String sql = "UPDATE auctions SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status);
            pstmt.setString(2, auctionId);

            return pstmt.executeUpdate() > 0;
        }
    }
}
