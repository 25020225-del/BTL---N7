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

/**
 * Data Access Object handles persistence operations and state synchronization
 * pipelines for auction entities.
 */
public class AuctionDAO {

    /**
     * Retrieves all auctions assigned to a specific seller ID.
     *
     * @param sellerId unique identifier of the target host.
     * @return a list of serialized maps containing auction properties.
     * @throws Exception if a database operation fails.
     */
    public List<Map<String, Object>> getAuctionsBySeller(String sellerId) throws Exception {
        String sql = "SELECT a.id, a.item_name, a.description, a.starting_price, a.current_price, " +
                "a.end_time, a.image_url, a.seller_id, a.status, a.bid_increment, a.item_type, " +
                "a.winning_bidder_id, a.start_time, a.duration_minutes, a.highest_max_bid, u.username AS winner_username " +
                "FROM auctions a " +
                "LEFT JOIN users u ON a.winning_bidder_id = u.id " +
                "WHERE a.seller_id = ?";
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sellerId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", rs.getString("id"));
                map.put("itemName", rs.getString("item_name"));
                map.put("description", rs.getString("description"));
                map.put("startingPrice", rs.getLong("starting_price"));
                map.put("currentPrice", rs.getLong("current_price"));
                String startTimeStr = rs.getString("start_time");
                if (startTimeStr != null && !startTimeStr.trim().isEmpty()) {
                    map.put("startTime", LocalDateTime.parse(startTimeStr).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                } else {
                    map.put("startTime", null);
                }
                String endTimeStr = rs.getString("end_time");
                if (endTimeStr != null && !endTimeStr.trim().isEmpty()) {
                    map.put("endTime", LocalDateTime.parse(endTimeStr).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                } else {
                    map.put("endTime", null);
                }
                map.put("imageUrl", rs.getString("image_url"));
                map.put("sellerId", rs.getString("seller_id"));
                map.put("status", rs.getString("status"));
                map.put("bidIncrement", rs.getLong("bid_increment"));
                map.put("itemType", rs.getString("item_type"));
                map.put("winningBidderId", rs.getString("winning_bidder_id"));
                map.put("winnerName", rs.getString("winner_username"));
                map.put("durationMinutes", rs.getInt("duration_minutes"));
                map.put("highestMaxBid", rs.getLong("highest_max_bid"));
                result.add(map);
            }
        }
        return result;
    }

    /**
     * Fetches a fully hydrated Auction aggregate model by its unique identifier.
     *
     * @param auctionId unique database primary key string.
     * @return fully populated {@link Auction} domain object, or null if not found.
     * @throws SQLException if retrieval fails.
     */
    public Auction getAuctionById(String auctionId) throws SQLException {
        String sql = "SELECT a.*, u.username AS winner_username FROM auctions a LEFT JOIN users u ON a.winning_bidder_id = u.id WHERE a.id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auctionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Auction auction = new Auction();
                    auction.setId(rs.getString("id"));

                    Item item = ItemFactory.createItem(
                            rs.getString("item_type"),
                            "ITM-" + rs.getString("id"),
                            rs.getString("item_name"),
                            rs.getString("description"),
                            rs.getLong("starting_price")
                    );
                    item.setImageUrl(rs.getString("image_url"));
                    auction.setItem(item);

                    User seller = new User();
                    seller.setId(rs.getString("seller_id"));
                    auction.setSeller(seller);

                    auction.setCurrentPrice(rs.getLong("current_price"));
                    auction.setHighestMaxBid(rs.getLong("highest_max_bid"));
                    auction.setBidIncrement(rs.getLong("bid_increment"));
                    auction.setDurationMinutes(rs.getInt("duration_minutes"));
                    String startTimeStr = rs.getString("start_time");
                    if (startTimeStr != null && !startTimeStr.trim().isEmpty()) {
                        auction.setStartTime(LocalDateTime.parse(startTimeStr));
                    }
                    String endTimeStr = rs.getString("end_time");
                    if (endTimeStr != null && !endTimeStr.trim().isEmpty()) {
                        auction.setEndTime(LocalDateTime.parse(endTimeStr));
                    }
                    auction.setStatus(rs.getString("status"));

                    String winnerId = rs.getString("winning_bidder_id");
                    if (winnerId != null) {
                        User winner = new User();
                        winner.setId(winnerId);
                        winner.setUserName(rs.getString("winner_username"));
                        auction.setWinningBidder(winner);
                    }

                    return auction;
                }
            }
        }
        return null;
    }

    /**
     * Persists a newly created auction instance into the repository schema.
     *
     * @param auction un-persisted target domain configuration.
     * @return true if row update succeeds.
     * @throws SQLException on database transaction failures.
     */
    public boolean addAuction(Auction auction) throws SQLException {
        String sql = "INSERT INTO auctions (id, item_name, description, starting_price, current_price, bid_increment, start_time, end_time, status, seller_id, image_url, winning_bidder_id, highest_max_bid, duration_minutes, item_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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

            pstmt.setString(8, auction.getEndTime() != null
                    ? auction.getEndTime().toString() : null);
            pstmt.setString(9, auction.getStatus());
            pstmt.setString(10, auction.getSeller().getId());
            pstmt.setString(11, item.getImageUrl());
            pstmt.setString(12, auction.getWinningBidder() != null ? auction.getWinningBidder().getId() : null);
            pstmt.setLong(13, auction.getHighestMaxBid());
            pstmt.setInt(14, auction.getDurationMinutes());
            pstmt.setString(15, item.getType());

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
        StringBuilder sql = new StringBuilder("SELECT a.id, a.item_name, a.description, a.starting_price, a.current_price, a.end_time, a.image_url, a.seller_id, a.bid_increment, a.status, a.item_type, a.winning_bidder_id, a.start_time, a.duration_minutes, a.highest_max_bid, u.username AS winner_username FROM auctions a LEFT JOIN users u ON a.winning_bidder_id = u.id WHERE a.status IN (");
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
                    map.put("itemName", rs.getString("item_name"));
                    map.put("description", rs.getString("description"));
                    map.put("startingPrice", rs.getLong("starting_price"));
                    map.put("currentPrice", rs.getLong("current_price"));
                    String startTimeStr = rs.getString("start_time");
                    if (startTimeStr != null && !startTimeStr.trim().isEmpty()) {
                        map.put("startTime", LocalDateTime.parse(startTimeStr).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                    } else {
                        map.put("startTime", null);
                    }
                    String endTimeStr = rs.getString("end_time");
                    if (endTimeStr != null && !endTimeStr.trim().isEmpty()) {
                        map.put("endTime", LocalDateTime.parse(endTimeStr).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                    } else {
                        map.put("endTime", null);
                    }
                    map.put("imageUrl", rs.getString("image_url"));
                    map.put("sellerId", rs.getString("seller_id"));
                    map.put("status", rs.getString("status"));
                    map.put("bidIncrement", rs.getLong("bid_increment"));
                    map.put("itemType", rs.getString("item_type"));
                    map.put("winningBidderId", rs.getString("winning_bidder_id"));
                    map.put("winnerName", rs.getString("winner_username"));
                    map.put("durationMinutes", rs.getInt("duration_minutes"));
                    map.put("highestMaxBid", rs.getLong("highest_max_bid"));
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

    public boolean updateAuctionStatusOpenToWaiting(String auctionId) throws SQLException {
        String sql = "UPDATE auctions SET status = ? WHERE id = ? AND status = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, Auction.STATUS_WAITING_FOR_BID);
            pstmt.setString(2, auctionId);
            pstmt.setString(3, Auction.STATUS_OPEN);
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean updateAuctionStatusEndingIfEndTimeMatches(String auctionId, String newStatus, LocalDateTime expectedEndTimeInDb) throws SQLException {
        String sql = "UPDATE auctions SET status = ? WHERE id = ? AND end_time = ? AND status = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newStatus);
            pstmt.setString(2, auctionId);
            pstmt.setString(3, expectedEndTimeInDb.toString());
            pstmt.setString(4, Auction.STATUS_RUNNING);
            return pstmt.executeUpdate() > 0;
        }
    }

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

    public boolean updateApprovalStatus(String auctionId, String status, LocalDateTime startTime, LocalDateTime endTime) throws SQLException {
        String sql = "UPDATE auctions SET status = ?, start_time = ?, end_time = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setString(2, startTime != null ? startTime.toString() : null);
            pstmt.setString(3, endTime != null ? endTime.toString() : null);
            pstmt.setString(4, auctionId);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Recovers stuck database records and outboxed state histories from un-synchronized crashes.
     *
     * @return list of recovered {@link Auction} aggregates requiring financial settlements.
     * @throws SQLException if batch execution queries fail.
     */
    public List<Auction> sweepOrphanAuctions() throws SQLException {
        List<Auction> pendingSettlementAuctions = new ArrayList<>();
        String now = LocalDateTime.now().toString();

        try (Connection conn = DatabaseManager.getConnection()) {
            String selectExpiredSql = "SELECT id FROM auctions WHERE status = 'RUNNING' AND end_time IS NOT NULL AND end_time < ?";
            String updateExpiredSql = "UPDATE auctions SET status = ? WHERE id = ? AND status = 'RUNNING' AND end_time IS NOT NULL";

            try (PreparedStatement selectStmt = conn.prepareStatement(selectExpiredSql);
                 PreparedStatement updateStmt = conn.prepareStatement(updateExpiredSql)) {
                selectStmt.setString(1, now);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        updateStmt.setString(1, Auction.STATUS_FINISHED);
                        updateStmt.setString(2, id);
                        updateStmt.executeUpdate();
                    }
                }
            }

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

            String startSql = "SELECT id FROM auctions WHERE status = 'OPEN' AND start_time <= ?";
            String updateStartSql = "UPDATE auctions SET status = ? WHERE id = ? AND status = 'OPEN'";
            try (PreparedStatement startStmt = conn.prepareStatement(startSql);
                 PreparedStatement updateStartStmt = conn.prepareStatement(updateStartSql)) {
                startStmt.setString(1, now);
                try (ResultSet rs = startStmt.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        updateStartStmt.setString(1, Auction.STATUS_WAITING_FOR_BID);
                        updateStartStmt.setString(2, id);
                        updateStartStmt.executeUpdate();
                    }
                }
            }
        }
        return pendingSettlementAuctions;
    }

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
}