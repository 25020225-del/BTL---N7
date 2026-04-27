package model;

import java.time.LocalDateTime;

public class WalletTransaction extends Entity {
    private String userId;
    private double amount; // + = deposit/refund, - = withdraw/bid
    private String description;
    private LocalDateTime createdAt;

    public WalletTransaction() { super(); }

    public WalletTransaction(String id, String userId, double amount, String description) {
        super(id);
        this.userId = userId;
        this.amount = amount;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String getInfo() {
        String type = (amount >= 0) ? "[+ In]" : "[- Out]";
        return type + " Amount: " + amount + " | Time: " + createdAt + " | Desc: " + description;
    }
}