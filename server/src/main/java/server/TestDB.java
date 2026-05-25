package server;

import database.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class TestDB {
    public static void main(String[] args) {
        try {
            DatabaseManager.initializeDatabase();
            System.out.println("DB Initialized");

            String sql = "INSERT INTO auctions (id, item_name, description, starting_price, current_price, bid_increment, start_time, end_time, status, seller_id, image_url, winning_bidder_id, highest_max_bid, duration_minutes, item_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                 
                pstmt.setString(1, "AUC-test-123");
                pstmt.setString(2, "Test Item");
                pstmt.setString(3, "Desc");
                pstmt.setLong(4, 1000);
                pstmt.setLong(5, 1000);
                pstmt.setLong(6, 100);
                pstmt.setString(7, java.time.LocalDateTime.now().toString());
                pstmt.setString(8, null);
                pstmt.setString(9, "PENDING");
                pstmt.setString(10, "A001");
                pstmt.setString(11, "http://url");
                pstmt.setString(12, null);
                pstmt.setLong(13, 0);
                pstmt.setInt(14, 60);
                pstmt.setString(15, "TANGIBLE");

                pstmt.executeUpdate();
                System.out.println("INSERT SUCCESS");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
