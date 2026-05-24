package model.item;

import model.base.Entity;

/**
 * Abstract base domain model capturing fundamental invariants, state properties,
 * and structural behavior for all auctionable products.
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
     * Initializes a bounded item context configuration with default pending metadata verification status.
     *
     * @param id            the unique primary identification key
     * @param itemName      the catalog title or name descriptor
     * @param description   detailed textual description of product features
     * @param startingPrice opening evaluation baseline monetary amount
     */
    public Item(String id, String itemName, String description, long startingPrice) {
        super(id);
        this.itemName = itemName;
        this.description = description;
        this.startingPrice = startingPrice;
        this.approvalStatus = "PENDING";
    }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public long getStartingPrice() { return startingPrice; }
    public void setStartingPrice(long startingPrice) { this.startingPrice = startingPrice; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public byte[] getFile() { return file; }
    public void setFile(byte[] file) { this.file = file; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @Override
    public String getInfo() {
        return "Item ID: " + this.getId()
                + " | Name: " + this.getItemName()
                + " | Description: " + description
                + " | Starting Price: VND " + startingPrice;
    }
}