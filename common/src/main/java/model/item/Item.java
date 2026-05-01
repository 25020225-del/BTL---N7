package model.item;

import model.base.Entity;

/**
 * Represents a generic item that can be placed in an auction.
 * This class serves as a base model containing core details such as the item's name,
 * description, starting price, and its administrative approval status.
 */
public class Item extends Entity {
    private String itemName;
    private String description;
    private double startingPrice;
    private String approvalStatus;

    /**
     * Default constructor.
     */
    public Item() {
        super();
    }

    /**
     * Constructs a new Item with the specified details.
     * The approval status is automatically set to "PENDING" upon creation,
     * requiring an administrator's review before it becomes active in the marketplace.
     *
     * @param id            The unique identifier for this item.
     * @param itemName      The name or title of the item.
     * @param description   A detailed description of the item's condition, features, etc.
     * @param startingPrice The initial starting price for the auction.
     */
    public Item(String id, String itemName, String description, double startingPrice) {
        super(id);
        this.itemName       = itemName;
        this.description    = description;
        this.startingPrice  = startingPrice;
        this.approvalStatus = "PENDING";
    }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }

    /**
     * Generates a formatted summary string containing the item's core details.
     *
     * @return A string displaying the item's ID, name, description, and starting price.
     */
    @Override
    public String getInfo() {
        return       "Item ID: "            + this.getId()
                + " | Name: "               + this.getItemName()
                + " | Description: "        + description
                + " | Starting Price: VND " + startingPrice;
    }
}