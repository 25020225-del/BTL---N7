package model.user;

/**
 * Specialized identity sub-profile representing session hosts and inventory providers.
 * Manages operational asset listings and coordinates approval processing pipelines.
 */
public class Seller extends User {

    public Seller() {
        super();
    }

    /**
     * Binds general identity properties into a structural seller account container.
     *
     * @param user baseline aggregate instance to transform
     */
    public Seller(User user) {
        super(user.getId(), user.getUserName(), user.getPassword(), user.getName(), "SELLER");
        this.setGood(user.isGood());
    }

    @Override
    public String getInfo() {
        return "[Seller] ID: " + this.getId() + " | Name: " + this.getName();
    }
}