package model;

/**
 * Represents a physical, tangible item in the auction system.
 * Examples include electronics, vehicles, clothing, or physical artwork.
 * Future extensions could include properties like weight, dimensions, and shipping methods.
 */
public class TangibleItem extends Item {

    public TangibleItem(String id, String itemName, String description, double startingPrice) {
        super(id, itemName, description, startingPrice);
    }

    @Override
    public String getInfo() {
        return    "[Tangible Asset]: "  + this.getItemName()
                + " | Start Price: VND " + this.getStartingPrice();
    }
}