package database.dao;

import database.DatabaseManager;
import model.auction.Auction;
import model.item.Item;
import server.ServerExtension.ClientManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static utils.ConsoleColors.BLUE;
import static utils.ConsoleColors.RED;
import static utils.ConsoleColors.RESET;
import static utils.ConsoleColors.YELLOW;

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

    /**
     * Scans the database for orphaned auctions (expired but not updated in RAM)
     * and updates their status.
     *
     * @return A list of auction IDs that were updated.
     * @throws SQLException if a database access error occurs.
     */
    public List<String> sweepOrphanAuctions() throws SQLException {
        List<String> updatedAuctionIds = new ArrayList<>();
        String selectSql = "SELECT id, end_time, current_price, starting_price, start_time, status FROM auctions WHERE status IN ('OPEN', 'RUNNING')";
        String updateSql = "UPDATE auctions SET status = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectSql);
             PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {

            ResultSet rs = selectStmt.executeQuery();

            while (rs.next()) {
                String id = rs.getString("id");
                LocalDateTime endTime = LocalDateTime.parse(rs.getString("end_time"));
                LocalDateTime startTime = LocalDateTime.parse(rs.getString("start_time"));
                String currentStatus = rs.getString("status");
                LocalDateTime now = LocalDateTime.now();

                // Case 1: The auction's end time has passed.
                if (now.isAfter(endTime)) {
                    double currentPrice = rs.getDouble("current_price");
                    double startPrice = rs.getDouble("starting_price");
                    String newStatus = (currentPrice > startPrice) ? Auction.STATUS_FINISHED : Auction.STATUS_CANCELED;

                    updateStmt.setString(1, newStatus);
                    updateStmt.setString(2, id);
                    updateStmt.executeUpdate();
                    updatedAuctionIds.add(id);
                    System.out.println("[System]: " + BLUE + "Swept and closed orphaned database auction: " + YELLOW + id + RESET + " -> " + newStatus);
                }
                // Case 2: The auction should be running but is still OPEN in DB.
                else if (currentStatus.equals(Auction.STATUS_OPEN) && now.isAfter(startTime)) {
                    updateStmt.setString(1, Auction.STATUS_RUNNING);
                    updateStmt.setString(2, id);
                    updateStmt.executeUpdate();
                    updatedAuctionIds.add(id);
                    System.out.println("[System]: " + BLUE + "Swept and started orphaned database auction: " + YELLOW + id + RESET);
                }
            }
        }
        return updatedAuctionIds;
    }
}
