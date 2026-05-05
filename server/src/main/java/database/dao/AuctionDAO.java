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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                    // Cần khởi tạo hoặc inject AuctionDAO trước đó
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

    public List<Map<String, Object>> getAuctionsByStatus(String... statuses) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT id, item_name, description, starting_price, current_price, end_time, image_url FROM auctions WHERE status IN (");
        for (int i = 0; i < statuses.length; i++) {
            sql.append("?");
            if (i < statuses.length - 1) sql.append(", ");
        }
        sql.append(")");

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < statuses.length; i++) {
                pstmt.setString(i + 1, statuses[i]);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", rs.getString("id"));
                    map.put("item_name", rs.getString("item_name"));
                    map.put("description", rs.getString("description"));
                    map.put("starting_price", rs.getDouble("starting_price"));
                    map.put("current_price", rs.getDouble("current_price"));
                    map.put("end_time", rs.getString("end_time"));
                    map.put("image_url", rs.getString("image_url"));
                    list.add(map);
                }
            }
        }
        return list;
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
     * Sweeps the database for auctions that have expired while the server was offline
     * or those that were not properly transitioned in RAM.
     *
     * @return A list of Auction objects that were finalized during the sweep.
     * @throws SQLException if a database access error occurs.
     */
    public List<Auction> sweepOrphanAuctions() throws SQLException {
        List<Auction> finishedAuctions = new ArrayList<>();
        String now = LocalDateTime.now().toString();

        // 1. Find auctions that should have ended but are still in RUNNING/OPEN status
        String selectSql = "SELECT * FROM auctions WHERE (status = 'RUNNING' OR status = 'OPEN') AND end_time < ?";
        String updateSql = "UPDATE auctions SET status = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectSql);
             PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {

            selectStmt.setString(1, now);
            try (ResultSet rs = selectStmt.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
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
            }

            // 2. Find auctions that should have started but are still in OPEN status
            String startSql = "SELECT id FROM auctions WHERE status = 'OPEN' AND start_time <= ? AND end_time > ?";
            try (PreparedStatement startStmt = conn.prepareStatement(startSql)) {
                startStmt.setString(1, now);
                startStmt.setString(2, now);
                try (ResultSet rs = startStmt.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        updateStmt.setString(1, "RUNNING");
                        updateStmt.setString(2, id);
                        updateStmt.executeUpdate();
                        System.out.println("[System]: " + BLUE + "Swept and started orphaned database auction: " + YELLOW + id + RESET);
                    }
                }
            }
        }
        return finishedAuctions;
    }

    public boolean deleteAuction(String auctionId) throws SQLException {
        String sql = "DELETE FROM auctions WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auctionId);
            return pstmt.executeUpdate() > 0;
        }
    }
}
