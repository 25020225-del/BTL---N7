package model;

public class Vehicle extends Item {

    public Vehicle(String id, String itemName, String description, double startingPrice ) {
        super(id, itemName, description, startingPrice);
    }

    @Override
    public String getInfo(){
        return    "[Vehicle] "          + this.getItemName()
                + " | Start Price: VND" + this.getStartingPrice();
    }
}