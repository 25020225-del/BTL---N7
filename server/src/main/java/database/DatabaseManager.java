package database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages database connectivity and lifecycle for the auction system.
 * This class utilizes HikariCP for efficient connection pooling and handles
 * the initial creation and migration of SQLite database tables.
 */
public class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    /**
     * The JDBC connection URL for the SQLite database, configured with WAL mode.
     */
    private static final String DB_URL = "jdbc:sqlite:auction_system.db?journal_mode=WAL";

    /**
     * The pooled data source instance used to provide database connections.
     */
    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);

        // SQLite configuration: limited to 5 concurrent connections to prevent file locking issues
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);

        // Optimization: Enable Write-Ahead Logging (WAL) for better read/write concurrency in SQLite
        config.addDataSourceProperty("journal_mode", "WAL");

        // Set a busy timeout of 5000ms to allow threads to wait if the database is temporarily locked
        config.addDataSourceProperty("busy_timeout", "5000");

        dataSource = new HikariDataSource(config);
    }

    /**
     * Retrieves a connection from the HikariCP connection pool.
     *
     * @return A {@link Connection} object ready for database operations.
     * @throws SQLException If a connection cannot be established or retrieved from the pool.
     */
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Initializes the relational database schema.
     * This method performs the following tasks:
     * <ul>
     *     <li>Enables foreign key constraints.</li>
     *     <li>Creates core tables: users, wallets, auctions, transactions, and auto-bids.</li>
     *     <li>Handles legacy table upgrades for TOTP security features.</li>
     *     <li>Seeds a default administrator account with a secure hashed password.</li>
     * </ul>
     */
    public static void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Ensure SQLite enforces relational integrity
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

            // Migration: Add is_blocked column to users if it doesn't exist
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN is_blocked INTEGER DEFAULT 0;");
            } catch (SQLException e) {
                // Column likely already exists
            }

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

            // LEGACY MIGRATION: Safely add TOTP columns if they are missing from older versions
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN totp_secret TEXT;");
                stmt.execute("ALTER TABLE users ADD COLUMN is_totp_enabled INTEGER DEFAULT 0;");
                stmt.execute("ALTER TABLE wallets ADD COLUMN locked_balance REAL DEFAULT 0.0;");
                log.info("Successfully upgraded user table");
            } catch (SQLException ignored) {
                // Columns already exist
            }

            try {
                stmt.execute("ALTER TABLE auctions ADD COLUMN image_url TEXT;");
            } catch (SQLException ignored) {
            }

            try {
                stmt.execute("ALTER TABLE auctions ADD COLUMN winning_bidder_id TEXT;");
            } catch (SQLException ignored) {
            }

            try {
                stmt.execute("ALTER TABLE auctions ADD COLUMN highest_max_bid REAL DEFAULT 0.0;");
            } catch (SQLException ignored) {
            }

            log.info("Successfully upgraded auctions table");

            // SEED DATA: Insert default admin user if not present
            String adminPass = BCrypt.hashpw("123456", BCrypt.gensalt(12));
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
                log.info("Successfully added is_bot column to bid_transactions");
            } catch (SQLException ignored) {
                // Cột đã tồn tại, bỏ qua an toàn
            }

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

            log.info("Successfully initialized");

        } catch (SQLException e) {
            log.error("Database initialization error: {}", e.getMessage());
        }
    }
}