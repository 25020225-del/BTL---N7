package database.dao;

import database.DatabaseManager;
import model.auction.Auction;
import model.item.Item;
import model.item.ItemFactory;
import model.user.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static utils.ConsoleColors.*;

public class AuctionDAO {

    public List<Map<String, Object>> getAuctionsBySeller(String sellerId) throws Exception {
        String sql = "SELECT id, item_name, description, starting_price, current_price, " +
                "end_time, image_url, seller_id, status, bid_increment FROM auctions WHERE seller_id = ?";
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sellerId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("id",            rs.getString("id"));
                map.put("itemName",     rs.getString("item_name"));
                map.put("description",   rs.getString("description"));
                map.put("startingPrice",rs.getLong("starting_price"));
                map.put("currentPrice", rs.getLong("current_price"));
                map.put("endTime", LocalDateTime.parse(rs.getString("end_time")).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                map.put("imageUrl",     rs.getString("image_url"));
                map.put("sellerId",      rs.getString("seller_id"));
                map.put("status",        rs.getString("status"));
                map.put("bidIncrement",        rs.getLong("bid_increment"));
                result.add(map);
            }
        }
        return result;
    }

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
                            rs.getLong("starting_price")
                    );
                    item.setImageUrl(rs.getString("image_url"));
                    auction.setItem(item);

                    User seller = new User();
                    // Cần khởi tạo hoặc inject AuctionDAO trước đó
                    seller.setId(rs.getString("seller_id"));
                    auction.setSeller(seller);

                    auction.setCurrentPrice(rs.getLong("current_price"));
                    auction.setHighestMaxBid(rs.getLong("highest_max_bid"));
                    auction.setBidIncrement(rs.getLong("bid_increment"));
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
            pstmt.setLong(4, item.getStartingPrice());
            pstmt.setLong(5, auction.getCurrentPrice());
            pstmt.setLong(6, auction.getBidIncrement());
            pstmt.setString(7, auction.getStartTime().toString());
            pstmt.setString(8, auction.getEndTime().toString());
            pstmt.setString(9, auction.getStatus());
            pstmt.setString(10, auction.getSeller().getId());
            pstmt.setString(11, item.getImageUrl());
            pstmt.setString(12, auction.getWinningBidder() != null ? auction.getWinningBidder().getId() : null);
            pstmt.setLong(13, auction.getHighestMaxBid());

            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean updateAuction(Auction auction, String newName, String newDesc, long newStartPrice, LocalDateTime newStartTime, LocalDateTime newEndTime, String newStatus) throws SQLException {
        String sql = "UPDATE auctions SET item_name = ?, description = ?, starting_price = ?, current_price = ?, start_time = ?, end_time = ?, status = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newName);
            pstmt.setString(2, newDesc);
            pstmt.setLong(3, newStartPrice);
            pstmt.setLong(4, newStartPrice);
            pstmt.setString(5, newStartTime.toString());
            pstmt.setString(6, newEndTime.toString());
            pstmt.setString(7, newStatus);
            pstmt.setString(8, auction.getId());

            return pstmt.executeUpdate() > 0;
        }
    }

    public List<Map<String, Object>> getAuctionsByStatus(String... statuses) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT id, item_name, description, starting_price, current_price, end_time, image_url, seller_id, status FROM auctions WHERE status IN (");
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
                    map.put("itemName", rs.getString("item_name")); // Match frontend expectations
                    map.put("description", rs.getString("description"));
                    map.put("startingPrice", rs.getLong("starting_price"));
                    map.put("currentPrice", rs.getLong("current_price"));
                    map.put("endTime", LocalDateTime.parse(rs.getString("end_time")).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                    map.put("imageUrl", rs.getString("image_url"));
                    map.put("sellerId", rs.getString("seller_id"));
                    map.put("status",rs.getString("status"));
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
     * Optimistic transition OPEN → RUNNING: succeeds only if the row is still OPEN.
     */
    public boolean updateAuctionStatusOpenToRunning(String auctionId) throws SQLException {
        String sql = "UPDATE auctions SET status = ? WHERE id = ? AND status = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, Auction.STATUS_RUNNING);
            pstmt.setString(2, auctionId);
            pstmt.setString(3, Auction.STATUS_OPEN);

            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Terminal status update (PAID/CANCELED) with optimistic locking on {@code end_time}.
     * Fails when anti-sniping or another writer changed {@code end_time} since the RAM snapshot was taken.
     */
    public boolean updateAuctionStatusEndingIfEndTimeMatches(
            String auctionId,
            String newStatus,
            LocalDateTime expectedEndTimeInDb
    ) throws SQLException {
        String sql = "UPDATE auctions SET status = ? WHERE id = ? AND end_time = ? "
                + "AND (status = ? OR status = ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newStatus);
            pstmt.setString(2, auctionId);
            pstmt.setString(3, expectedEndTimeInDb.toString());
            pstmt.setString(4, Auction.STATUS_RUNNING);
            pstmt.setString(5, Auction.STATUS_OPEN);

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
     * [ARCHITECT FIX]: Implemented State-As-Outbox Pattern to recover crashed settlements.
     */
    public List<Auction> sweepOrphanAuctions() throws SQLException {
        List<Auction> pendingSettlementAuctions = new ArrayList<>();
        String now = LocalDateTime.now().toString();

        try (Connection conn = DatabaseManager.getConnection()) {
            // 1. Quét và chốt sổ các phiên hết hạn (RUNNING/OPEN -> FINISHED)
            String selectExpiredSql = "SELECT id FROM auctions WHERE (status = 'RUNNING' OR status = 'OPEN') AND end_time < ?";
            String updateExpiredSql = "UPDATE auctions SET status = ? WHERE id = ? AND (status = 'RUNNING' OR status = 'OPEN')";

            try (PreparedStatement selectStmt = conn.prepareStatement(selectExpiredSql);
                 PreparedStatement updateStmt = conn.prepareStatement(updateExpiredSql)) {

                selectStmt.setString(1, now);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        updateStmt.setString(1, Auction.STATUS_FINISHED);
                        updateStmt.setString(2, id);
                        if (updateStmt.executeUpdate() > 0) {
                            System.out.println("[System]: " + utils.ConsoleColors.BLUE + "Swept and closed orphaned database auction: " + utils.ConsoleColors.YELLOW + id + utils.ConsoleColors.RESET + " -> FINISHED");
                        }
                    }
                }
            }

            // 2. [RECONCILIATION] Quét TOÀN BỘ các phiên đang kẹt ở trạng thái FINISHED
            // Bao gồm những phiên vừa bị ép đóng ở Bước 1, VÀ những phiên bị kẹt do server crash trước đó.
            String stuckSql = "SELECT id, current_price, highest_max_bid, seller_id, winning_bidder_id FROM auctions WHERE status = 'FINISHED'";
            try (PreparedStatement stuckStmt = conn.prepareStatement(stuckSql);
                 ResultSet rs = stuckStmt.executeQuery()) {

                while (rs.next()) {
                    Auction auction = new Auction();
                    auction.setId(rs.getString("id"));
                    auction.setCurrentPrice(rs.getLong("current_price"));
                    auction.setHighestMaxBid(rs.getLong("highest_max_bid"));

                    User seller = new User();
                    seller.setId(rs.getString("seller_id"));
                    auction.setSeller(seller);

                    String winningBidderId = rs.getString("winning_bidder_id");
                    if (winningBidderId != null) {
                        User winner = new User();
                        winner.setId(winningBidderId);
                        auction.setWinningBidder(winner);
                    }
                    pendingSettlementAuctions.add(auction);
                }
            }

            // 3. Quét các phiên mồ côi cần start (OPEN -> RUNNING)
            String startSql = "SELECT id FROM auctions WHERE status = 'OPEN' AND start_time <= ? AND end_time > ?";
            String updateStartSql = "UPDATE auctions SET status = 'RUNNING' WHERE id = ?";
            try (PreparedStatement startStmt = conn.prepareStatement(startSql);
                 PreparedStatement updateStartStmt = conn.prepareStatement(updateStartSql)) {

                startStmt.setString(1, now);
                startStmt.setString(2, now);
                try (ResultSet rs = startStmt.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        updateStartStmt.setString(1, id);
                        if (updateStartStmt.executeUpdate() > 0) {
                            System.out.println("[System]: " + utils.ConsoleColors.BLUE + "Swept and started orphaned database auction: " + utils.ConsoleColors.YELLOW + id + utils.ConsoleColors.RESET);
                        }
                    }
                }
            }
        }

        // Trả về danh sách chờ thanh toán để AuctionMonitor đẩy vào TransactionManager
        return pendingSettlementAuctions;
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
