package controller;

import database.DatabaseManager;
import database.dao.UserDAO;
import model.user.Admin;
import model.user.User;
import org.mindrot.jbcrypt.BCrypt;
import service.TOTPService;

import java.sql.Connection;
import java.sql.SQLException;

import static utils.ConsoleColors.*;

/**
 * Controller responsible for managing user-related operations, including
 * authentication, account registration, and security configurations.
 * This class handles password hashing using BCrypt and coordinates the
 * integration of Time-based One-Time Password (TOTP) for 2FA.
 */
public class UserController {

    private final TOTPService totpService;
    private final UserDAO userDAO;

    /**
     * Constructs the controller with the necessary services and DAOs.
     * This implementation follows the Dependency Injection pattern to facilitate 
     * easier testing and decoupling.
     *
     * @param userDAO     The DAO responsible for user-related database transactions.
     * @param totpService The service responsible for 2FA/TOTP logic.
     */
    public UserController(UserDAO userDAO, TOTPService totpService) {
        this.userDAO = userDAO;
        this.totpService = totpService;
    }

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
     * and QR URL. On failure, returns an error message.
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

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try {
                if (userDAO.isUsernameExists(conn, userName)) {
                    System.out.println("[System](UserController): Registration failed, username \"" + YELLOW + userName + RESET + "\" already exists");
                    return "[Error]: Username \"" + userName + "\" already exists.";
                }

                String newId = "U-" + System.currentTimeMillis();
                String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12));
                String secretKey = totpService.createSecretKey();

                userDAO.createUserAndWallet(conn, newId, userName, hashedPassword, name, role, secretKey);

                conn.commit();

                String qrUrl = totpService.getQRUrl(userName, secretKey);
                System.out.println("[System]: \"" + YELLOW + userName + RESET + "\" has just created an account. 2FA Enabled.");
                return "SUCCESS|" + secretKey + "|" + qrUrl;

            } catch (SQLException e) {
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
     * {@code null} otherwise.
     */
    public User login(String userName, String password) {
        try {
            User user = userDAO.findUserByUsername(userName);

            if (user != null && BCrypt.checkpw(password, user.getPassword())) {
                System.out.println("[System]: \"" + YELLOW + user.getName() + RESET + "\" (" + YELLOW + user.getRole() + RESET + ") has logged in.");

                if (user.getRole().equalsIgnoreCase("ADMIN")) {
                    return new Admin(user.getId(), user.getUserName(), user.getPassword(), user.getName());
                } else {
                    return user;
                }
            }
        } catch (SQLException e) {
            System.out.println("[Database](UserController): Database error during login: " + RED + e.getMessage() + RESET);
        }

        System.out.println("[System](UserController): Login failed for \"" + YELLOW + userName + RESET + "\"");
        return null;
    }
}
