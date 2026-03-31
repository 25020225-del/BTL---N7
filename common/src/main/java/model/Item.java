package model;

public abstract class Item extends Entity {
    protected String itemName;
    protected String description;
    protected double startingPrice;

    public Item() {}

    public Item(String id, String itemName, String description, double startingPrice) {
        super(id);
        this.itemName = itemName;
        this.description = description;
        this.startingPrice = startingPrice;
    }


    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public double getStartingPrice() { return startingPrice; }
}