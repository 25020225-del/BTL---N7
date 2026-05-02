package controller;

import database.DatabaseManager;
import model.user.*;
import org.mindrot.jbcrypt.BCrypt;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static utils.ConsoleColors.*;

/**
 * Controller responsible for managing user-related operations, including
 * authentication, account registration, and security configurations.
 * This class handles password hashing using BCrypt and coordinates the
 * integration of Time-based One-Time Password (TOTP) for 2FA.
 */
public class UserController {

    /** Service utility for handling 2FA secret key generation and QR URL construction. */
    private final service.TOTPService totpService = new service.TOTPService();

    /**
     * Registers a new user in the system and initializes their digital wallet.
     * This method is synchronized to prevent race conditions during username availability checks.
     * It performs an atomic database transaction to ensure that a user is not created
     * without a corresponding wallet.
     *
     * @param userName The unique username for the new account.
     * @param password The raw password to be encrypted via BCrypt.
     * @param name     The display name of the user.
     * @param role     The requested system role (e.g., "USER", "BIDDER", "SELLER").
     * @return A status string. On success, returns "SUCCESS" appended with the 2FA secret
     *         and QR URL. On failure, returns an error message.
     */
    public synchronized String register(String userName, String password, String name, String role) {
        // Security check: Prevent self-registration as an Administrator
        if (role.equalsIgnoreCase("ADMIN")) {
            return "[Error]: You are not allowed to register an Admin account yourself";
        }

        // Validate that the requested role is within allowed standard user parameters
        if (!role.equalsIgnoreCase("BIDDER") && !role.equalsIgnoreCase("SELLER") && !role.equalsIgnoreCase("USER")) {
            return "[Error]: Invalid role";
        }

        String checkSql  = "SELECT 1 FROM users WHERE username = ?";
        String insertSql = "INSERT INTO users (id, username, password, name, role, is_good, totp_secret, is_totp_enabled) VALUES (?, ?, ?, ?, ?, 0, ?, 1)";
        String insertWalletSql = "INSERT INTO wallets (user_id, balance) VALUES (?, 0.0)";

        try (Connection conn = DatabaseManager.getConnection()) {
            // Disable auto-commit to manage User and Wallet creation as a single atomic unit
            conn.setAutoCommit(false);

            try {
                // Verify username uniqueness
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setString(1, userName);
                    ResultSet rs = checkStmt.executeQuery();
                    if (rs.next()) {
                        System.out.println("[System](UserController): Registration failed, username \"" + YELLOW + userName + RESET + "\" already exists");
                        return "[Error]: Username \"" + userName + "\" already exists.";
                    }
                }

                String newId = "U-" + System.currentTimeMillis();

                // Securely hash the password using BCrypt with a workload factor of 12
                String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12));

                // Initialize 2FA components
                String secretKey = totpService.createSecretKey();
                String qrUrl = totpService.getQRUrl(userName, secretKey);

                // Insert the User record
                try (PreparedStatement insertUserStmt = conn.prepareStatement(insertSql)) {
                    insertUserStmt.setString(1, newId);
                    insertUserStmt.setString(2, userName);
                    insertUserStmt.setString(3, hashedPassword);
                    insertUserStmt.setString(4, name);
                    insertUserStmt.setString(5, role.toUpperCase());
                    insertUserStmt.setString(6, secretKey);

                    insertUserStmt.executeUpdate();
                }

                // Initialize the User's digital wallet with a 0.0 balance
                try (PreparedStatement insertWalletStmt = conn.prepareStatement(insertWalletSql)) {
                    insertWalletStmt.setString(1, newId);
                    insertWalletStmt.executeUpdate();
                }

                // Finalize the transaction
                conn.commit();

                System.out.println("[System]: \"" + YELLOW + userName + RESET + "\" has just created an account. 2FA Enabled.");
                return "SUCCESS|" + secretKey + "|" + qrUrl;
            } catch (SQLException e) {
                // Revert all changes if any part of the registration fails
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            System.out.println("[Database](UserController): Database error during registration: " + RED + e.getMessage() + RESET);
            return "[Error]: Database connection failed. Please try again later.";
        }
    }

    /**
     * Authenticates a user based on their username and password.
     *
     * @param userName The username provided during login.
     * @param password The plain-text password to be verified against the stored hash.
     * @return A specific {@link User} subclass (User or Admin) if authentication succeeds;
     *         {@code null} otherwise.
     */
    public User login(String userName, String password) {
        String sql = "SELECT * FROM users WHERE username = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userName);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String dbHash = rs.getString("password");

                // Validate password hash using BCrypt
                if (BCrypt.checkpw(password, dbHash)) {
                    String id      = rs.getString("id");
                    String name    = rs.getString("name");
                    String role    = rs.getString("role");
                    boolean isGood = rs.getInt("is_good") == 1;

                    System.out.println("[System]: \"" + YELLOW + name + RESET + "\" (" + YELLOW + role + RESET + ") has logged in.");

                    // Instantiate the appropriate object type based on the assigned system role
                    if (role.equalsIgnoreCase("ADMIN")) {
                        return new Admin(id, userName, password, name);
                    } else {
                        User user = new User(id, userName, password, name, role);
                        user.setGood(isGood);
                        return user;
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("[Database](UserController): Database error during login: " + RED + e.getMessage() + RESET);
        }

        System.out.println("[System](UserController): Login failed for \"" + YELLOW + userName + RESET + "\"");
        return null;
    }
}