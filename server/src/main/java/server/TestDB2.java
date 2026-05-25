package server;

import database.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class TestDB2 {
    public static void main(String[] args) {
        try {
            DatabaseManager.initializeDatabase();
            System.out.println("DB Initialized");

            String sql = "INSERT INTO auctions (id, item_name, description, starting_price, current_price, bid_increment, start_time, end_time, status, seller_id, image_url, winning_bidder_id, highest_max_bid, item_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                 
                pstmt.setString(1, "AUC-test-124");
                pstmt.setString(2, "Test Item 2");
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
                pstmt.setString(14, "TANGIBLE");

                pstmt.executeUpdate();
                System.out.println("INSERT SUCCESS 14 COLS");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
