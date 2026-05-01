package model.item;

/**
 * Represents a service or experience in the auction system.
 * Examples include event tickets, travel vouchers, or consulting sessions.
 * Future extensions could include validity periods or service locations.
 */
public class ServicePackage extends Item {

    public ServicePackage(String id, String itemName, String description, double startingPrice) {
        super(id, itemName, description, startingPrice);
    }

    @Override
    public String getInfo() {
        return    "[Service Package]: " + this.getItemName()
                + " | Start Price: VND " + this.getStartingPrice();
    }
}