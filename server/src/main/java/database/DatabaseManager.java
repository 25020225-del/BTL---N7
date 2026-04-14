package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:auction_system.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Bật khóa ngoại (Foreign Key)
            stmt.execute("PRAGMA foreign_keys = ON;");

            // 1. Tạo bảng users (Giữ nguyên)
            String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                    "id TEXT PRIMARY KEY, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "password TEXT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "role TEXT NOT NULL, " +
                    "is_good INTEGER DEFAULT 0" +
                    ");";
            stmt.execute(createUsersTable);

            String insertAdmin = "INSERT OR IGNORE INTO users (id, username, password, name, role, is_good) " +
                    "VALUES ('A001', 'admin', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 'Super Admin', 'ADMIN', 1);";
            stmt.execute(insertAdmin);

            // ==========================================
            // 2. TẠO BẢNG AUCTIONS (MỚI THÊM)
            // ==========================================
            String createAuctionsTable = "CREATE TABLE IF NOT EXISTS auctions (" +
                    "id TEXT PRIMARY KEY, " +
                    "item_name TEXT NOT NULL, " +
                    "description TEXT, " +
                    "starting_price REAL NOT NULL, " +
                    "current_price REAL NOT NULL, " +
                    "bid_increment REAL NOT NULL, " +
                    "start_time TEXT NOT NULL, " +
                    "end_time TEXT NOT NULL, " +
                    "status TEXT NOT NULL, " +
                    "seller_id TEXT NOT NULL, " +
                    "FOREIGN KEY (seller_id) REFERENCES users(id)" + // Liên kết với người bán
                    ");";
            stmt.execute(createAuctionsTable);
            // Thêm đoạn này vào bên dưới lệnh tạo bảng auctions trong DatabaseManager.java
            String createBidTransactionsTable = "CREATE TABLE IF NOT EXISTS bid_transactions (" +
                    "id TEXT PRIMARY KEY, " +
                    "auction_id TEXT NOT NULL, " +
                    "bidder_id TEXT NOT NULL, " +
                    "bid_amount REAL NOT NULL, " +
                    "bid_time TEXT NOT NULL, " +
                    "FOREIGN KEY (auction_id) REFERENCES auctions(id), " +
                    "FOREIGN KEY (bidder_id) REFERENCES users(id)" +
                    ");";
            stmt.execute(createBidTransactionsTable);
            // Thêm đoạn này vào cuối hàm initializeDatabase() trong DatabaseManager.java
            String createAutoBidsTable = "CREATE TABLE IF NOT EXISTS auto_bids (" +
                    "id TEXT PRIMARY KEY, " +
                    "auction_id TEXT NOT NULL, " +
                    "bidder_id TEXT NOT NULL, " +
                    "max_bid REAL NOT NULL, " +
                    "increment_amount REAL NOT NULL, " +
                    "is_active INTEGER DEFAULT 1, " + // 1 là đang kích hoạt, 0 là đã dừng
                    "FOREIGN KEY (auction_id) REFERENCES auctions(id), " +
                    "FOREIGN KEY (bidder_id) REFERENCES users(id), " +
                    "UNIQUE(auction_id, bidder_id) " + // Đảm bảo mỗi người chỉ có 1 cấu hình AutoBid cho 1 món hàng
                    ");";
            stmt.execute(createAutoBidsTable);

            System.out.println("Success");

            } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
        }
    }
}