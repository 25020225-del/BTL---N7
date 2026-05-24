package model.finance;

import model.base.Entity;

import java.time.LocalDateTime;

/**
 * Domain entity logging localized ledger asset alterations, tracking historical balance flows.
 */
public class WalletTransaction extends Entity {

    private String userId;
    private long amount;
    private String description;
    private LocalDateTime createdAt;

    public WalletTransaction() {
        super();
    }

    /**
     * Main ledger mapping constructor automatically provisioning timestamps.
     *
     * @param id          distinct audited tracking primary identity key
     * @param userId      owner identifier profile target key
     * @param amount      numerical asset delta mutation (positive for inflows, negative for outflows)
     * @param description contextual explanation clarifying transaction origin points
     */
    public WalletTransaction(String id, String userId, long amount, String description) {
        super(id);
        this.userId = userId;
        this.amount = amount;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String getInfo() {
        String type = (amount >= 0) ? "[+ In]" : "[- Out]";
        return type + " Balance updated: " + amount + " | Reason: " + description + " | Time: " + createdAt;
    }
}