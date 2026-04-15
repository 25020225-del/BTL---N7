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

            // 1. TẠO BẢNG USERS (Đã bổ sung 2 cột cho tính năng 2FA/TOTP)
            String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                    "id TEXT PRIMARY KEY, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "password TEXT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "role TEXT NOT NULL, " +
                    "is_good INTEGER DEFAULT 0, " +
                    "totp_secret TEXT, " +                 // Cột mới: Lưu chìa khóa bí mật của Google Authenticator
                    "is_totp_enabled INTEGER DEFAULT 0" +  // Cột mới: 1 là đã bật 2FA, 0 là chưa bật
                    ");";
            stmt.execute(createUsersTable);

            // 1.1 TỰ ĐỘNG NÂNG CẤP BẢNG CHO DATABASE CŨ
            // Nếu bạn đang dùng file DB cũ chưa có 2 cột này, đoạn code này sẽ tự động thêm vào mà không làm mất dữ liệu.
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN totp_secret TEXT;");
                stmt.execute("ALTER TABLE users ADD COLUMN is_totp_enabled INTEGER DEFAULT 0;");
                System.out.println("[Database] Đã nâng cấp bảng users thành công (Thêm cột 2FA).");
            } catch (SQLException e) {
                // Sẽ ném lỗi nếu cột đã tồn tại rồi, ta bơ đi cho chương trình chạy tiếp
            }

            // 2. TẠO TÀI KHOẢN ADMIN
            String insertAdmin = "INSERT OR IGNORE INTO users (id, username, password, name, role, is_good, is_totp_enabled) " +
                    "VALUES ('A001', 'admin', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 'Super Admin', 'ADMIN', 1, 0);";
            stmt.execute(insertAdmin);

            // 3. TẠO BẢNG AUCTIONS
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
                    "FOREIGN KEY (seller_id) REFERENCES users(id)" +
                    ");";
            stmt.execute(createAuctionsTable);

            // 4. TẠO BẢNG BID_TRANSACTIONS
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

            // 5. TẠO BẢNG AUTO_BIDS
            String createAutoBidsTable = "CREATE TABLE IF NOT EXISTS auto_bids (" +
                    "id TEXT PRIMARY KEY, " +
                    "auction_id TEXT NOT NULL, " +
                    "bidder_id TEXT NOT NULL, " +
                    "max_bid REAL NOT NULL, " +
                    "increment_amount REAL NOT NULL, " +
                    "is_active INTEGER DEFAULT 1, " +
                    "FOREIGN KEY (auction_id) REFERENCES auctions(id), " +
                    "FOREIGN KEY (bidder_id) REFERENCES users(id), " +
                    "UNIQUE(auction_id, bidder_id) " +
                    ");";
            stmt.execute(createAutoBidsTable);

            System.out.println("[Database] Successfully initialized");

        } catch (SQLException e) {
            System.err.println("[Database] Initialization error: " + e.getMessage());
        }
    }
}