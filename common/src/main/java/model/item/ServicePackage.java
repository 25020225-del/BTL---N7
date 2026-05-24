package model.item;

/**
 * Concrete domain entity representing a contractual service agreement or experiential token package.
 */
public class ServicePackage extends Item {

    public ServicePackage(String id, String itemName, String description, long startingPrice) {
        super(id, itemName, description, startingPrice);
        this.setType(ItemFactory.TYPE_SERVICE);
    }

    @Override
    public String getInfo() {
        return "[Service Package]: " + this.getItemName()
                + " | Start Price: VND " + this.getStartingPrice();
    }
}