package model.user;

/**
 * Specialized identity aggregate representing an administrative overseer profile.
 * Possesses privileges for resource modification, pipeline approvals, and enforcement controls.
 */
public class Admin extends User {

    /**
     * Core constructor mapping complete structural credential components.
     */
    public Admin(String id, String userName, String password, String name) {
        super(id, userName, password, name, "Admin");
    }

    /**
     * Identity elevation constructor wrapping an active baseline client profile.
     * Forces standard operational boundaries into privileged administrative settings.
     *
     * @param admin baseline active profile container instance to evaluate
     */
    public Admin(User admin) {
        super(admin.getId(), admin.getUserName(), admin.getPassword(), admin.getName(), "Admin");
    }

    @Override
    public String getInfo() {
        return "[Admin] ID: " + this.getId()
                + " | Name: " + this.getName() + " (System Administrator)";
    }
}