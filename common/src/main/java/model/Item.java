package model;

public class Item extends Entity {
    private String itemName;
    private String description;
    private double startingPrice;
    private String approvalStatus;

    public Item() {
        super();
    }

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

    @Override
    public String getInfo() {
        return       "Item ID: "            + this.getId()
                + " | Name: "               + this.getItemName()
                + " | Description: "        + description
                + " | Starting Price: VND " + startingPrice;
    }
}