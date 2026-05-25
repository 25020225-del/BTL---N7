package model.item;

/**
 * Concrete domain entity representing an abstract virtual product or intangible digital asset.
 */
public class DigitalItem extends Item {

    public DigitalItem() {        
        super();
        this.setType(ItemFactory.TYPE_DIGITAL);
    }

    public DigitalItem(String id, String itemName, String description, long startingPrice) {
        super(id, itemName, description, startingPrice);
        this.setType(ItemFactory.TYPE_DIGITAL);
    }

    @Override
    public String getInfo() {
        return "[Digital Product]: " + this.getItemName()
                + " | Start Price: VND " + this.getStartingPrice();
    }
}