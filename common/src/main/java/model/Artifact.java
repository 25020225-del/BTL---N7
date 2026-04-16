package model;

public class Artifact extends Item {

    public Artifact(String id, String itemName, String description, double startingPrice, int warrantyMonths) {
        super(id, itemName, description, startingPrice);
    }

    @Override
    public String getInfo() {
        return    "[Artifact]: "        + this.itemName
                + " | Start Price: VND" + this.startingPrice;
    }

}