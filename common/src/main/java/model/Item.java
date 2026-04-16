package model;

public abstract class Item extends Entity {

    protected String itemName;
    protected String description;
    protected double startingPrice;
    private String approvalStatus;

    public Item() {
        super();
    }

    public Item(String id, String itemName, String description, double startingPrice) {
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

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }

    @Override
    public String getInfo() {
        return "Item: " + itemName + " | Description: " + description + " | Starting Price: VND " + startingPrice;
    }
}