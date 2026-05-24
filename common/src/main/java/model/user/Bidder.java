package model.user;

/**
 * Specialized identity sub-profile representing active auction room participants.
 * Executes balance pledges and monitors asset transaction entries.
 */
public class Bidder extends User {

    public Bidder() {
        super();
    }

    /**
     * Binds general identity properties into a structural bidder account container.
     *
     * @param user baseline aggregate instance to transform
     */
    public Bidder(User user) {
        super(user.getId(), user.getUserName(), user.getPassword(), user.getName(), "BUYER");
        this.setGood(user.isGood());
    }

    @Override
    public String getInfo() {
        return "[Bidder] ID: " + this.getId() + " | Name: " + this.getName();
    }
}