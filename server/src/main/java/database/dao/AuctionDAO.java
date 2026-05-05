package database.dao;

import database.DatabaseManager;
import model.auction.Auction;
import model.item.Item;
import model.item.ItemFactory;
import model.user.User;
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

    public Auction getAuctionById(String auctionId) throws SQLException {
        String sql = "SELECT * FROM auctions WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auctionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Auction auction = new Auction();
                    auction.setId(rs.getString("id"));
                    
                    model.item.Item item = ItemFactory.createItem(
                            ItemFactory.TYPE_TANGIBLE,
                            "ITM-" + System.currentTimeMillis(),
                            rs.getString("item_name"),
                            rs.getString("description"),
                            rs.getDouble("starting_price")
                    );
                    item.setImageUrl(rs.getString("image_url"));
                    auction.setItem(item);
                    
                    User seller = new User();
                    seller.setId(rs.getString("seller_id"));
                    auction.setSeller(seller);
                    
                    auction.setCurrentPrice(rs.getDouble("current_price"));
                    auction.setHighestMaxBid(rs.getDouble("highest_max_bid"));
                    auction.setBidIncrement(rs.getDouble("bid_increment"));
                    auction.setStartTime(LocalDateTime.parse(rs.getString("start_time")));
                    auction.setEndTime(LocalDateTime.parse(rs.getString("end_time")));
                    auction.setStatus(rs.getString("status"));
                    
                    String winnerId = rs.getString("winning_bidder_id");
                    if (winnerId != null) {
                        User winner = new User();
                        winner.setId(winnerId);
                        auction.setWinningBidder(winner);
                    }
                    
                    return auction;
                }
            }
        }
        return null;
    }

    public boolean addAuction(Auction auction) throws SQLException {
        String sql = "INSERT INTO auctions (id, item_name, description, starting_price, current_price, bid_increment, start_time, end_time, status, seller_id, image_url, winning_bidder_id, highest_max_bid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
            pstmt.setString(12, auction.getWinningBidder() != null ? auction.getWinningBidder().getId() : null);
            pstmt.setDouble(13, auction.getHighestMaxBid());

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
     * Retrieves the scheduled start and end times for a specific auction.
     *
     * @param auctionId The unique identifier of the auction.
     * @return An array of LocalDateTime where index 0 is start_time and index 1 is end_time.
     * @throws SQLException if a database access error occurs.
     */
    public LocalDateTime[] getAuctionTimes(String auctionId) throws SQLException {
        String sql = "SELECT start_time, end_time FROM auctions WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auctionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String startTimeStr = rs.getString("start_time");
                    String endTimeStr = rs.getString("end_time");
                    LocalDateTime start = (startTimeStr != null && !startTimeStr.trim().isEmpty()) ? LocalDateTime.parse(startTimeStr) : null;
                    LocalDateTime end = (endTimeStr != null && !endTimeStr.trim().isEmpty()) ? LocalDateTime.parse(endTimeStr) : null;
                    return new LocalDateTime[]{start, end};
                }
            }
        }
        return null;
    }

    /**
     * Updates the approval status and timing of an auction.
     *
     * @param auctionId The unique identifier of the auction.
     * @param status    The new status.
     * @param startTime The updated start time.
     * @param endTime   The updated end time.
     * @return true if the update was successful.
     * @throws SQLException if a database access error occurs.
     */
    public boolean updateApprovalStatus(String auctionId, String status, LocalDateTime startTime, LocalDateTime endTime) throws SQLException {
        String sql = "UPDATE auctions SET status = ?, start_time = ?, end_time = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setString(2, startTime.toString());
            pstmt.setString(3, endTime.toString());
            pstmt.setString(4, auctionId);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Scans the database for orphaned auctions (expired but not updated in RAM)
     * and updates their status.
     *
     * @return A list of Auction objects that were transitioned to FINISHED, for financial settlement.
     * @throws SQLException if a database access error occurs.
     */
    public List<Auction> sweepOrphanAuctions() throws SQLException {
        List<Auction> finishedAuctions = new ArrayList<>();
        String selectSql = "SELECT id, end_time, current_price, starting_price, start_time, status, seller_id, winning_bidder_id, highest_max_bid FROM auctions WHERE status IN ('OPEN', 'RUNNING')";
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
                    String winningBidderId = rs.getString("winning_bidder_id");
                    
                    String newStatus = (winningBidderId != null && currentPrice > startPrice) ? Auction.STATUS_PAID : Auction.STATUS_CANCELED;

                    updateStmt.setString(1, newStatus);
                    updateStmt.setString(2, id);
                    updateStmt.executeUpdate();

                    if (newStatus.equals(Auction.STATUS_PAID)) {
                        // Create a minimal Auction object for financial settlement
                        Auction auction = new Auction();
                        auction.setId(id);
                        auction.setCurrentPrice(currentPrice);
                        auction.setHighestMaxBid(rs.getDouble("highest_max_bid"));
                        
                        User seller = new User();
                        seller.setId(rs.getString("seller_id"));
                        auction.setSeller(seller);
                        
                        if (winningBidderId != null) {
                            User winner = new User();
                            winner.setId(winningBidderId);
                            auction.setWinningBidder(winner);
                        }
                        
                        finishedAuctions.add(auction);
                    }
                    
                    System.out.println("[System]: " + BLUE + "Swept and closed orphaned database auction: " + YELLOW + id + RESET + " -> " + newStatus);
                }
                // Case 2: The auction should be running but is still OPEN in DB.
                else if (currentStatus.equals(Auction.STATUS_OPEN) && now.isAfter(startTime)) {
                    updateStmt.setString(1, Auction.STATUS_RUNNING);
                    updateStmt.setString(2, id);
                    updateStmt.executeUpdate();
                    System.out.println("[System]: " + BLUE + "Swept and started orphaned database auction: " + YELLOW + id + RESET);
                }
            }
        }
        return finishedAuctions;
    }
}
