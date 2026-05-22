package model.item;

/**
 * Represents a digital product in the auction system.
 * Examples include software licenses, domain names, eBooks, or NFTs.
 * Future extensions could include properties like download links or file sizes.
 */
public class DigitalItem extends Item {
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