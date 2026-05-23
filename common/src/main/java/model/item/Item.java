package model.item;

import model.base.Entity;

/**
 * Abstract base class for all items that can be placed in an auction.
 * Defines core attributes shared by every item (name, description, starting price,
 * image, approval status, and category type). Concrete subclasses must specify the
 * category they represent (e.g. {@link TangibleItem}, {@link DigitalItem},
 * {@link ServicePackage}) and provide a meaningful {@link #getInfo()} implementation.
 *
 * <p>This class is intentionally abstract: the auction system should never deal
 * with a plain "unknown-type" item; every item must belong to a concrete category.</p>
 */
public abstract class Item extends Entity {
    private String itemName;
    private String description;
    private long startingPrice;
    private String imageUrl;
    private byte[] file;
    private String approvalStatus;
    private String type = "IDK";

    protected Item() {
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
    public Item(String id, String itemName, String description, long startingPrice) {
        super(id);
        this.itemName = itemName;
        this.description = description;
        this.startingPrice = startingPrice;
        this.approvalStatus = "PENDING";
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(long startingPrice) {
        this.startingPrice = startingPrice;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public byte[] getFile() {
        return file;
    }

    public void setFile(byte[] file) {
        this.file = file;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Generates a formatted summary string containing the item's core details.
     *
     * @return A string displaying the item's ID, name, description, and starting price.
     */
    @Override
    public String getInfo() {
        return "Item ID: " + this.getId()
                + " | Name: " + this.getItemName()
                + " | Description: " + description
                + " | Starting Price: VND " + startingPrice;
    }
}