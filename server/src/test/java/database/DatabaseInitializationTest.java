package database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Database Integrations — Schema & Insertion Tests")
class DatabaseInitializationTest {

    @BeforeAll
    static void init() {
        DatabaseManager.initializeDatabase();
    }

    @AfterEach
    void tearDown() {
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM auctions WHERE id LIKE 'AUC-test-%'");
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Test 1: Chèn thử nghiệm phiên đấu giá đầy đủ 15 cột (Thay thế TestDB)")
    void testAuctionInsertion_With15Columns() {
        String sql = "INSERT INTO auctions (id, item_name, description, starting_price, " +
                "current_price, bid_increment, start_time, end_time, status, seller_id, " +
                "image_url, winning_bidder_id, highest_max_bid, duration_minutes, item_type) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        assertDoesNotThrow(() -> {
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, "AUC-15-" + java.util.UUID.randomUUID());
                pstmt.setString(2, "Test Item 15 Cols");
                pstmt.setString(3, "Description context");
                pstmt.setLong(4, 1000L);
                pstmt.setLong(5, 1000L);
                pstmt.setLong(6, 100L);
                pstmt.setString(7, LocalDateTime.now().toString());
                pstmt.setString(8, null);
                pstmt.setString(9, "PENDING");
                pstmt.setString(10, "A001");
                pstmt.setString(11, "http://url-image");
                pstmt.setString(12, null);
                pstmt.setLong(13, 0L);
                pstmt.setInt(14, 60);
                pstmt.setString(15, "TANGIBLE");

                int rowsAffected = pstmt.executeUpdate();
                assertEquals(1, rowsAffected, "Hàng dữ liệu phải được chèn thành công!");
            }
        });
    }

    @Test
    @DisplayName("Test 2: Chèn thử nghiệm phiên đấu giá rút gọn 14 cột (Thay thế TestDB2)")
    void testAuctionInsertion_With14Columns() {
        String sql = "INSERT INTO auctions (id, item_name, description, starting_price, " +
                "current_price, bid_increment, start_time, end_time, status, seller_id, " +
                "image_url, winning_bidder_id, highest_max_bid, item_type) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        assertDoesNotThrow(() -> {
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, "AUC-14-" + java.util.UUID.randomUUID());
                pstmt.setString(2, "Test Item 14 Cols");
                pstmt.setString(3, "Description context");
                pstmt.setLong(4, 1000L);
                pstmt.setLong(5, 1000L);
                pstmt.setLong(6, 100L);
                pstmt.setString(7, LocalDateTime.now().toString());
                pstmt.setString(8, null);
                pstmt.setString(9, "PENDING");
                pstmt.setString(10, "A001");
                pstmt.setString(11, "http://url-image");
                pstmt.setString(12, null);
                pstmt.setLong(13, 0L);
                pstmt.setString(14, "TANGIBLE");

                int rowsAffected = pstmt.executeUpdate();
                assertEquals(1, rowsAffected, "Hàng dữ liệu 14 cột phải được chèn thành công!");
            }
        });
    }
}