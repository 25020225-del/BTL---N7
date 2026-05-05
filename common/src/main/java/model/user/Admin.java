package model.user;

/**
 * Represents a system administrator with elevated privileges within the auction platform.
 * Administrators are responsible for overseeing the system, approving or rejecting
 * pending auctions, and managing user activities.
 */
public class Admin extends User {

    /**
     * Constructs a new Admin with the specified credentials and personal information.
     * The role is automatically set to "Admin".
     *
     * @param id       The unique identifier for the administrator.
     * @param userName The login username.
     * @param userPass The encrypted password.
     * @param name     The real name or display name of the administrator.
     */
    public Admin(String id, String userName, String userPass, String name) {
        super(id, userName, userPass, name, "Admin");
    }

    /**
     * Constructs an Admin by elevating an existing standard {@link User} object.
     * Extracts the core credentials from the provided user and forces the role to "Admin".
     *
     * @param admin The existing User object to be elevated to an administrator.
     */
    public Admin(User admin) {
        super(admin.getId(), admin.getUserName(), admin.getUserPass(), admin.getName(), "Admin");
    }

    /**
     * Generates a formatted summary string specifically identifying this user
     * as a System Administrator.
     *
     * @return A string containing the admin tag, ID, and display name.
     */
    @Override
    public String getInfo() {
        return "[Admin] ID: " + this.getId()
                + " | Name: " + this.getName() + " (System Administrator)";
    }
}