package model;

public class Art extends Item {

    public Art(String id, String itemName, String description, double startingPrice) {
        super(id, itemName, description, startingPrice);
    }

    @Override
    public String getInfo() {
        return    "[Art]: "             + this.getItemName()
                + " | Start Price: VND" + this.getStartingPrice();
    }

}