package model.user;

import model.base.Entity;

/**
 * Represents a unified user within the auction system.
 * This class consolidates the previously separated Bidder and Seller roles into a single
 * entity, capable of both participating in auctions and hosting them.
 */
public class User extends Entity {

    private boolean totpEnabled;
    private String totpSecret;  // chỉ cần nếu muốn cache, có thể bỏ
    private String userName;
    private String password; // Renamed from userPass
    private String name;
    private String role;

    /**
     * A flag indicating whether the user is a verified or trusted participant.
     * Trusted users may have privileges such as bypassing the pending approval queue
     * when creating new auctions.
     */
    private boolean isGood;

    /**
     * Default constructor.
     */
    public User() {
        super();
    }

    /**
     * Constructs a new User with full credentials and a specifically assigned role.
     *
     * @param id       The unique identifier for the user.
     * @param userName The login username.
     * @param password The user's encrypted password.
     * @param name     The display name or real name of the user.
     * @param role     The system role assigned to the user (e.g., "USER", "ADMIN").
     */
    public User(String id, String userName, String password, String name, String role) {
        super(id);
        this.userName = userName;
        this.password = password;
        this.name = name;
        this.role = role;
    }

    /**
     * Constructs a new User without initially specifying a role.
     *
     * @param id       The unique identifier for the user.
     * @param userName The login username.
     * @param password The user's encrypted password.
     * @param name     The display name or real name of the user.
     */
    public User(String id, String userName, String password, String name) {
        super(id);
        this.userName = userName;
        this.password = password;
        this.name = name;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    // Getter for password
    public String getPassword() {
        return password;
    }

    // Setter for password
    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isGood() {
        return isGood;
    }

    public void setGood(boolean good) {
        this.isGood = good;
    }

    public boolean isTotpEnabled() { return totpEnabled; }
    public void setTotpEnabled(boolean totpEnabled) { this.totpEnabled = totpEnabled; }
    /**
     * Generates a formatted summary string containing the user's core details.
     * If the user is marked as trusted (isGood), a "[TRUSTED]" tag is prepended to the output.
     *
     * @return A string displaying the user's ID, username, name, and role.
     */
    @Override
    public String getInfo() {
        String tag = this.isGood() ? "[TRUSTED] " : "";
        return tag + "ID: " + this.getId()
                + " | Username: " + this.userName
                + " | Name: " + this.name
                + " | Role: " + this.role;
    }
}