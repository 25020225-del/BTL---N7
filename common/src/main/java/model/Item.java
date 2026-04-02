package model;

public abstract class Item extends Entity {
    protected String itemName;
    protected String description;
    protected double startingPrice;
    private String approvalStatus; // Pending --> Open --> Running --> Finished --> Paid/ Cancelled

    // Hàm tạo rỗng (Bắt buộc để Jackson/Gson giải mã JSON)
    public Item() {
        super(); // Gọi hàm tạo rỗng của lớp cha Entity cho chắc cú
    }

    // Hàm tạo đầy đủ tham số
    public Item(String id, String itemName, String description, double startingPrice) {
        super(id);
        this.itemName = itemName;
        this.description = description;
        this.startingPrice = startingPrice;
        this.approvalStatus = "PENDING";
    }

    // --- GETTER & SETTER ĐẦY ĐỦ (Bắt buộc cho JSON) ---

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }

    // --- PHƯƠNG THỨC LẤY THÔNG TIN CHUNG ---

    @Override
    public String getInfo() {
        return "Sản phẩm: " + itemName + " | Mô tả: " + description + " | Giá khởi điểm: VND" + startingPrice;
    }
}