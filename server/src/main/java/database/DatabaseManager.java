package database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static utils.ConsoleColors.*;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:auction_system.db?journal_mode=WAL";

    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setMaximumPoolSize(5); // SQLite does not support highly concurrent writes
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);

        // Enable Write-Ahead Logging (WAL) for better read/write concurrency
        config.addDataSourceProperty("journal_mode", "WAL");

        // Instruct SQLite to queue threads and wait up to 5000ms if the DB is locked
        config.addDataSourceProperty("busy_timeout", "5000");

        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA foreign_keys = ON;");

            String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                    "id TEXT PRIMARY KEY, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "password TEXT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "role TEXT NOT NULL, " +
                    "is_good INTEGER DEFAULT 0, " +
                    "totp_secret TEXT, " +
                    "is_totp_enabled INTEGER DEFAULT 0" +
                    ");";
            stmt.execute(createUsersTable);

            String createWalletsTable = "CREATE TABLE IF NOT EXISTS wallets (" +
                    "user_id TEXT PRIMARY KEY, " +
                    "balance REAL DEFAULT 0.0, " +
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

            try {
                stmt.execute("ALTER TABLE users ADD COLUMN totp_secret TEXT;");
                stmt.execute("ALTER TABLE users ADD COLUMN is_totp_enabled INTEGER DEFAULT 0;");
                System.out.println("[Database]: " + GREEN + "Successfully upgraded user table" + RESET);
            } catch (SQLException ignored) {
            }

            String adminPass = BCrypt.hashpw("123456", BCrypt.gensalt(12));
            String insertAdmin = "INSERT OR IGNORE INTO users (id, username, password, name, role, is_good, is_totp_enabled) " +
                    "VALUES ('A001', 'admin', '" + adminPass + "', 'Super Admin', 'ADMIN', 1, 0);";
            stmt.execute(insertAdmin);

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

            System.out.println("[Database]:" + GREEN + " Successfully initialized" + RESET);

        } catch (SQLException e) {
            System.out.println("[Error]: Database initialization error: " + RED + e.getMessage() + RESET);
        }
    }
}