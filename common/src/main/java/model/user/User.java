package model.user;

import model.base.Entity;

/**
 * Represents a unified user within the auction system.
 * This class consolidates the previously separated Bidder and Seller roles into a single
 * entity, capable of both participating in auctions and hosting them.
 */
public class User extends Entity {

    private String userName;
    private String userPass;
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
     * @param userPass The user's encrypted password.
     * @param name     The display name or real name of the user.
     * @param role     The system role assigned to the user (e.g., "USER", "ADMIN").
     */
    public User(String id, String userName, String userPass, String name, String role) {
        super(id);
        this.userName = userName;
        this.userPass = userPass;
        this.name = name;
        this.role = role;
    }

    /**
     * Constructs a new User without initially specifying a role.
     *
     * @param id       The unique identifier for the user.
     * @param userName The login username.
     * @param userPass The user's encrypted password.
     * @param name     The display name or real name of the user.
     */
    public User(String id, String userName, String userPass, String name) {
        super(id);
        this.userName = userName;
        this.userPass = userPass;
        this.name = name;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserPass() {
        return userPass;
    }

    public void setUserPass(String userPass) {
        this.userPass = userPass;
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