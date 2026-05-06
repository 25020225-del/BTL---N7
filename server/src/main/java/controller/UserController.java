package controller;

import database.DatabaseManager;
import database.dao.UserDAO;
import model.user.Admin;
import model.user.User;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.TOTPService;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Controller responsible for managing user-related operations.
 */
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final TOTPService totpService;
    private final UserDAO userDAO;

    public UserController(UserDAO userDAO, TOTPService totpService) {
        this.userDAO = userDAO;
        this.totpService = totpService;
    }

    /**
     * Registers a new user and initializes wallet.
     */
    public String register(String userName, String password, String name, String role) {
        if (role.equalsIgnoreCase("ADMIN")) {
            return "[Error]: You are not allowed to register an Admin account yourself";
        }

        if (!role.equalsIgnoreCase("BIDDER") && !role.equalsIgnoreCase("SELLER") && !role.equalsIgnoreCase("USER")) {
            return "[Error]: Invalid role";
        }

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try {
                String newId = "U-" + System.currentTimeMillis();
                String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(12));
                String secretKey = totpService.createSecretKey();

                userDAO.createUserAndWallet(conn, newId, userName, hashedPassword, name, role, secretKey);

                conn.commit();

                String qrUrl = totpService.getQRUrl(userName, secretKey);
                log.info("User {} registered. 2FA enabled.", userName);
                return "SUCCESS|" + secretKey + "|" + qrUrl;

            } catch (SQLException e) {
                conn.rollback();
                if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed: users.username")) {
                    log.info("Registration failed: username {} already exists", userName);
                    return "[Error]: Username \"" + userName + "\" already exists.";
                }
                throw e;
            }

        } catch (SQLException e) {
            log.error("Database error during registration", e);
            return "[Error]: Database connection failed. Please try again later.";
        }
    }

    /**
     * Authenticates by username/password.
     */
    public User login(String userName, String password) {
        try {
            User user = userDAO.findUserByUsername(userName);

            if (user != null && BCrypt.checkpw(password, user.getPassword())) {
                log.info("User {} ({}) logged in.", user.getName(), user.getRole());

                if (user.getRole().equalsIgnoreCase("ADMIN")) {
                    return new Admin(user.getId(), user.getUserName(), user.getPassword(), user.getName());
                } else {
                    return user;
                }
            }
        } catch (SQLException e) {
            log.error("Database error during login", e);
        }

        log.info("Login failed for username {}", userName);
        return null;
    }
}
