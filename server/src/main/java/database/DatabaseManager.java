package database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Quản lý kết nối cơ sở dữ liệu sử dụng HikariCP.
 * Tích hợp cơ chế tự động nhận diện môi trường Test để bảo vệ CSDL chính.
 */
public class DatabaseManager {
    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private static final HikariDataSource dataSource;
    private static final boolean IS_TEST_ENV;

    // Khối static khởi tạo Connection Pool ngay khi class được load vào JVM
    static {
        IS_TEST_ENV = detectTestEnvironment();
        HikariConfig config = new HikariConfig();

        if (IS_TEST_ENV) {
            log.info("🛠️ Bật chế độ TEST: Bẻ lái sang In-Memory Database để bảo vệ CSDL chính!");
            // Với SQLite in-memory, bắt buộc phải dùng mode=memory&cache=shared
            config.setJdbcUrl("jdbc:sqlite:file:testdb?mode=memory&cache=shared");
            config.setMaximumPoolSize(10); // Cho phép nhiều luồng chạy test đồng thời hơn
        } else {
            config.setJdbcUrl("jdbc:sqlite:auction_system.db");
            config.setMaximumPoolSize(5);
        }

        config.setDriverClassName("org.sqlite.JDBC");

        // --- Tối ưu hóa SQLite ---
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "10000"); // Nới lỏng thời gian chờ lên 10s cho test đa luồng

        // [ARCHITECT FIX]: Xóa sổ lỗi SQLITE_LOCKED_SHAREDCACHE
        // Ép giao dịch bắt đầu bằng BEGIN IMMEDIATE để lấy Write Lock ngay lập tức, chống Deadlock
        config.addDataSourceProperty("transactionMode", "IMMEDIATE");

        dataSource = new HikariDataSource(config);
    }

    /**
     * Thuật toán nhận diện môi trường:
     * Quét các lớp đang nằm trong StackTrace của luồng hiện tại. Nếu có dấu hiệu
     * của framework kiểm thử (JUnit/TestNG), lập tức bật cờ môi trường Test.
     */
    private static boolean detectTestEnvironment() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String className = element.getClassName();
            if (className.startsWith("org.junit.") ||
                    className.startsWith("org.testng.") ||
                    className.startsWith("org.apache.maven.surefire.")) {
                return true;
            }
        }
        return false;
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public static void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Bật tính năng ràng buộc khóa ngoại (Foreign Key) cho SQLite
            stmt.execute("PRAGMA foreign_keys = ON;");

            // --- USER MANAGEMENT SCHEMA ---
            String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                    "id TEXT PRIMARY KEY, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "password TEXT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "role TEXT NOT NULL, " +
                    "is_good INTEGER DEFAULT 0, " +
                    "totp_secret TEXT, " +
                    "is_totp_enabled INTEGER DEFAULT 0, " +
                    "is_blocked INTEGER DEFAULT 0" +
                    ");";
            stmt.execute(createUsersTable);

            try {
                stmt.execute("ALTER TABLE users ADD COLUMN is_blocked INTEGER DEFAULT 0;");
            } catch (SQLException ignored) {}

            // --- FINANCIAL SCHEMA ---
            String createWalletsTable = "CREATE TABLE IF NOT EXISTS wallets (" +
                    "user_id TEXT PRIMARY KEY, " +
                    "balance REAL DEFAULT 0.0, " +
                    "locked_balance REAL DEFAULT 0.0, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id)" +
                    ");";
            stmt.execute(createWalletsTable);

            String createWalletTxnTable = "CREATE TABLE IF NOT EXISTS wallet_transactions (" +
                    "id TEXT PRIMARY KEY, " +
                    "user_id TEXT NOT NULL, " +
                    "amount REAL NOT NULL, " +
                    "description TEXT, " +
                    "created_at TEXT NOT NULL, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id)" +
                    ");";
            stmt.execute(createWalletTxnTable);

            // MIGRATION TÁCH BIỆT
            try { stmt.execute("ALTER TABLE users ADD COLUMN totp_secret TEXT;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE users ADD COLUMN is_totp_enabled INTEGER DEFAULT 0;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE wallets ADD COLUMN locked_balance REAL DEFAULT 0.0;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE auctions ADD COLUMN image_url TEXT;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE auctions ADD COLUMN winning_bidder_id TEXT;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE auctions ADD COLUMN highest_max_bid REAL DEFAULT 0.0;"); } catch (SQLException ignored) {}

            log.info("Successfully upgraded database schemas");

            // SEED DATA: Tạo tài khoản Admin mặc định
            String adminPass = org.mindrot.jbcrypt.BCrypt.hashpw("123456", org.mindrot.jbcrypt.BCrypt.gensalt(12));
            String insertAdmin = "INSERT OR IGNORE INTO users (id, username, password, name, role, is_good, is_totp_enabled) " +
                    "VALUES ('A001', 'admin', '" + adminPass + "', 'Super Admin', 'ADMIN', 1, 0);";
            stmt.execute(insertAdmin);

            // --- AUCTION SYSTEM SCHEMA ---
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
                    "image_url TEXT, " +
                    "winning_bidder_id TEXT, " +
                    "highest_max_bid REAL DEFAULT 0.0, " +
                    "FOREIGN KEY (seller_id) REFERENCES users(id), " +
                    "FOREIGN KEY (winning_bidder_id) REFERENCES users(id)" +
                    ");";
            stmt.execute(createAuctionsTable);

            String createBidTransactionsTable = "CREATE TABLE IF NOT EXISTS bid_transactions (" +
                    "id TEXT PRIMARY KEY, " +
                    "auction_id TEXT NOT NULL, " +
                    "bidder_id TEXT NOT NULL, " +
                    "bid_amount REAL NOT NULL, " +
                    "bid_time TEXT NOT NULL, " +
                    "is_bot INTEGER DEFAULT 0, " +
                    "FOREIGN KEY (auction_id) REFERENCES auctions(id), " +
                    "FOREIGN KEY (bidder_id) REFERENCES users(id)" +
                    ");";
            stmt.execute(createBidTransactionsTable);

            try {
                stmt.execute("ALTER TABLE bid_transactions ADD COLUMN is_bot INTEGER DEFAULT 0;");
            } catch (SQLException ignored) {}

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

            log.info("Successfully initialized Database (Test Env: {})", IS_TEST_ENV);

        } catch (SQLException e) {
            log.error("Database initialization error: {}", e.getMessage());
        }
    }
}