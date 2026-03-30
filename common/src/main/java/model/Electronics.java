package model;

public class Electronics extends Item {

    public Electronics(String id, String itemName, String description, double startingPrice, int warrantyMonths) {
        super(id, itemName, description, startingPrice);
    }

    @Override
    public String getInfo() {
        return "[Electronics] " + this.itemName + " | Start Price: VND" + this.startingPrice ;
    }
}