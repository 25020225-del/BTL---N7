package database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Manages database connections using HikariCP connection pooling.
 * Automatically detects the test environment to protect the production database.
 */
public class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private static final HikariDataSource dataSource;
    private static final boolean IS_TEST_ENV;

    static {
        IS_TEST_ENV = detectTestEnvironment();
        HikariConfig config = new HikariConfig();

        if (IS_TEST_ENV) {
            log.info("TEST mode active: Using isolated physical test database.");
            config.setJdbcUrl("jdbc:sqlite:test_auction_system.db");
            config.setMaximumPoolSize(10);
        } else {
            config.setJdbcUrl("jdbc:sqlite:auction_system.db");
            config.setMaximumPoolSize(5);
        }

        config.setDriverClassName("org.sqlite.JDBC");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "10000");
        config.addDataSourceProperty("transactionMode", "IMMEDIATE");

        dataSource = new HikariDataSource(config);
    }

    /**
     * Detects whether code is currently running inside a test framework
     * by inspecting the current thread's stack trace for known test runner class prefixes.
     */
    private static boolean detectTestEnvironment() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String className = element.getClassName();
            if (className.startsWith("org.junit.")
                    || className.startsWith("org.testng.")
                    || className.startsWith("org.apache.maven.surefire.")) {
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

    /**
     * Creates all required database tables and applies schema migrations idempotently.
     * Throws {@link IllegalStateException} on failure so the server fails fast
     * rather than starting with a broken schema.
     *
     * @throws IllegalStateException if any critical DDL statement fails.
     */
    public static void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA foreign_keys = ON;");

            createUsersTables(stmt);
            createFinancialTables(stmt);
            createAuctionTables(stmt);
            createWithdrawalTables(stmt);
            applyMigrations(stmt);
            seedDefaultAdmin(conn);
            ResultSet rs = conn.getMetaData().getColumns(null, null, "auctions", "item_type");
            if (!rs.next()) {
                stmt.execute("ALTER TABLE auctions ADD COLUMN item_type VARCHAR(20) DEFAULT 'TANGIBLE'");
                log.info("Migration: added item_type column to auctions");
            }

            log.info("Database initialized successfully (testEnv={})", IS_TEST_ENV);

        } catch (SQLException e) {
            // Fail fast: a broken schema will cause silent data corruption later.
            throw new IllegalStateException("Critical failure during database initialization", e);
        }
    }

    /**
     * Creates the withdrawal_requests table and its indexes idempotently.
     * Called once during server startup via {@link #initializeDatabase()}.
     *
     * @param stmt An active {@link Statement} within the initialization connection.
     * @throws SQLException if any DDL statement fails.
     */
    private static void createWithdrawalTables(Statement stmt) throws SQLException {
        // Bảng chính
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS withdrawal_requests ("
                        + "id             TEXT PRIMARY KEY, "
                        + "user_id        TEXT    NOT NULL, "
                        + "amount         REAL    NOT NULL CHECK(amount > 0), "
                        + "payout_method  TEXT    NOT NULL, "
                        + "payout_details TEXT    NOT NULL, "
                        + "status         TEXT    NOT NULL DEFAULT 'PENDING' "
                        + "CHECK(status IN ('PENDING','APPROVED','REJECTED','COMPLETED')), "
                        + "created_at     TEXT    NOT NULL, "
                        + "processed_at   TEXT, "
                        + "admin_id       TEXT, "
                        + "FOREIGN KEY (user_id)  REFERENCES users(id), "
                        + "FOREIGN KEY (admin_id) REFERENCES users(id)"
                        + ");"
        );

        // Indexes tối ưu hóa
        stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_wr_status "
                        + "ON withdrawal_requests(status);"
        );
        stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_wr_user_id "
                        + "ON withdrawal_requests(user_id);"
        );
        stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_wr_user_status "
                        + "ON withdrawal_requests(user_id, status);"
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // Private DDL helpers
    // ─────────────────────────────────────────────────────────────────

    private static void createUsersTables(Statement stmt) throws SQLException {
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS users ("
                        + "id TEXT PRIMARY KEY, "
                        + "username TEXT UNIQUE NOT NULL, "
                        + "password TEXT NOT NULL, "
                        + "name TEXT NOT NULL, "
                        + "role TEXT NOT NULL, "
                        + "is_good INTEGER DEFAULT 0, "
                        + "totp_secret TEXT, "
                        + "is_totp_enabled INTEGER DEFAULT 0, "
                        + "totp_status TEXT DEFAULT 'DISABLED', "
                        + "temp_totp_secret TEXT, "
                        + "is_blocked INTEGER DEFAULT 0, "
                        + "totp_login_enabled INTEGER DEFAULT 0, "
                        + "totp_payment_enabled INTEGER DEFAULT 0"
                        + ");"
        );
    }

    private static void createFinancialTables(Statement stmt) throws SQLException {
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS wallets ("
                        + "user_id TEXT PRIMARY KEY, "
                        + "balance REAL DEFAULT 0.0, "
                        + "locked_balance REAL DEFAULT 0.0, "
                        + "FOREIGN KEY (user_id) REFERENCES users(id)"
                        + ");"
        );
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS wallet_transactions ("
                        + "id TEXT PRIMARY KEY, "
                        + "user_id TEXT NOT NULL, "
                        + "amount REAL NOT NULL, "
                        + "description TEXT, "
                        + "created_at TEXT NOT NULL, "
                        + "FOREIGN KEY (user_id) REFERENCES users(id)"
                        + ");"
        );
    }

    private static void createAuctionTables(Statement stmt) throws SQLException {
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS auctions ("
                        + "id TEXT PRIMARY KEY, "
                        + "item_name TEXT NOT NULL, "
                        + "description TEXT, "
                        + "starting_price REAL NOT NULL, "
                        + "current_price REAL NOT NULL, "
                        + "bid_increment REAL NOT NULL, "
                        + "start_time TEXT NOT NULL, "
                        + "end_time TEXT, "
                        + "status TEXT NOT NULL, "
                        + "seller_id TEXT NOT NULL, "
                        + "image_url TEXT, "
                        + "winning_bidder_id TEXT, "
                        + "highest_max_bid REAL DEFAULT 0.0, "
                        + "duration_minutes INTEGER DEFAULT 60, "
                        + "item_type VARCHAR(20) DEFAULT 'TANGIBLE', "
                        + "FOREIGN KEY (seller_id) REFERENCES users(id), "
                        + "FOREIGN KEY (winning_bidder_id) REFERENCES users(id)"
                        + ");"
        );
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS bid_transactions ("
                        + "id TEXT PRIMARY KEY, "
                        + "auction_id TEXT NOT NULL, "
                        + "bidder_id TEXT NOT NULL, "
                        + "bid_amount REAL NOT NULL, "
                        + "bid_time TEXT NOT NULL, "
                        + "is_bot INTEGER DEFAULT 0, "
                        + "FOREIGN KEY (auction_id) REFERENCES auctions(id), "
                        + "FOREIGN KEY (bidder_id) REFERENCES users(id)"
                        + ");"
        );
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS auto_bids ("
                        + "id TEXT PRIMARY KEY, "
                        + "auction_id TEXT NOT NULL, "
                        + "bidder_id TEXT NOT NULL, "
                        + "max_bid REAL NOT NULL, "
                        + "increment_amount REAL NOT NULL, "
                        + "is_active INTEGER DEFAULT 1, "
                        + "FOREIGN KEY (auction_id) REFERENCES auctions(id), "
                        + "FOREIGN KEY (bidder_id) REFERENCES users(id), "
                        + "UNIQUE(auction_id, bidder_id)"
                        + ");"
        );
    }

    /**
     * Applies additive schema migrations idempotently.
     * Each ALTER TABLE is wrapped in its own try-catch because SQLite
     * throws an exception when a column already exists; this is the
     * standard "IF NOT EXISTS" workaround for SQLite ALTER TABLE.
     */
    private static void applyMigrations(Statement stmt) throws SQLException {
        runMigration(stmt, "ALTER TABLE users ADD COLUMN is_blocked INTEGER DEFAULT 0;");
        runMigration(stmt, "ALTER TABLE users ADD COLUMN totp_secret TEXT;");
        runMigration(stmt, "ALTER TABLE users ADD COLUMN is_totp_enabled INTEGER DEFAULT 0;");
        runMigration(stmt, "ALTER TABLE users ADD COLUMN totp_status TEXT DEFAULT 'DISABLED';");
        runMigration(stmt, "ALTER TABLE users ADD COLUMN temp_totp_secret TEXT;");
        runMigration(stmt, "ALTER TABLE users ADD COLUMN totp_login_enabled INTEGER DEFAULT 0;");
        runMigration(stmt, "ALTER TABLE users ADD COLUMN totp_payment_enabled INTEGER DEFAULT 0;");
        runMigration(stmt, "ALTER TABLE wallets ADD COLUMN locked_balance REAL DEFAULT 0.0;");
        runMigration(stmt, "ALTER TABLE auctions ADD COLUMN image_url TEXT;");
        runMigration(stmt, "ALTER TABLE auctions ADD COLUMN winning_bidder_id TEXT;");
        runMigration(stmt, "ALTER TABLE auctions ADD COLUMN highest_max_bid REAL DEFAULT 0.0;");
        runMigration(stmt, "ALTER TABLE bid_transactions ADD COLUMN is_bot INTEGER DEFAULT 0;");
        runMigration(stmt, "ALTER TABLE users ADD COLUMN is_good INTEGER DEFAULT 0;");
        runMigration(stmt, "ALTER TABLE auctions ADD COLUMN duration_minutes INTEGER DEFAULT 60;");

        try {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_auctions_status_start ON auctions (status, start_time);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_auctions_status_end ON auctions (status, end_time);");
        } catch (SQLException e) {
            log.warn("Migration: Thất bại khi tạo index hỗ trợ thời gian: {}", e.getMessage());
        }

        try {
            stmt.execute(
                    "UPDATE users SET totp_status = 'ENABLED' "
                            + "WHERE is_totp_enabled = 1 AND (totp_status IS NULL OR totp_status = 'DISABLED');"
            );
        } catch (SQLException e) {
            log.warn("Migration: totp_status back-fill skipped: {}", e.getMessage());
        }
    }

    private static void runMigration(Statement stmt, String ddl) {
        try {
            stmt.execute(ddl);
        } catch (SQLException ignored) {
            // Column already exists — safe to ignore.
        }
    }

    /**
     * Seeds the default admin account using a parameterised statement to prevent SQL injection.
     * Uses INSERT OR IGNORE so re-running this method is always idempotent.
     */
    private static void seedDefaultAdmin(Connection conn) throws SQLException {
        String hashedPassword = BCrypt.hashpw("123456", BCrypt.gensalt(12));
        String sql = "INSERT OR IGNORE INTO users "
                + "(id, username, password, name, role, is_good, is_totp_enabled) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "A001");
            ps.setString(2, "admin");
            ps.setString(3, hashedPassword);
            ps.setString(4, "Super Admin");
            ps.setString(5, "ADMIN");
            ps.setInt(6, 1);
            ps.setInt(7, 0);
            ps.executeUpdate();
        }
    }
}