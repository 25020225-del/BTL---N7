package model.item;

/**
 * Concrete domain entity representing a physical inventory asset or material property item.
 */
public class TangibleItem extends Item {

    public TangibleItem(String id, String itemName, String description, long startingPrice) {
        super(id, itemName, description, startingPrice);
        this.setType(ItemFactory.TYPE_TANGIBLE);
    }

    @Override
    public String getInfo() {
        return "[Tangible Asset]: " + this.getItemName()
                + " | Start Price: VND " + this.getStartingPrice();
    }
}