package model.finance;

import model.base.Entity;

import java.time.LocalDateTime;

/**
 * Represents a financial record of a change in a user's wallet balance.
 * This entity tracks both inflows (deposits, refunds) and outflows (bids, withdrawals),
 * providing a descriptive context and a precise timestamp for auditing purposes.
 */
public class WalletTransaction extends Entity {
    private String userId;
    private double amount;
    private String description;
    private LocalDateTime createdAt;

    /**
     * Default constructor.
     */
    public WalletTransaction() {
        super();
    }

    /**
     * Constructs a new WalletTransaction and automatically records the creation timestamp.
     *
     * @param id          The unique identifier for this transaction.
     * @param userId      The ID of the user whose wallet is affected.
     * @param amount      The transaction amount. A positive value indicates an inflow (deposit/refund),
     *                    while a negative value indicates an outflow (withdrawal/bid).
     * @param description A brief explanation of the transaction's context or origin.
     */
    public WalletTransaction(String id, String userId, double amount, String description) {
        super(id);
        this.userId = userId;
        this.amount = amount;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Generates a formatted summary string of this wallet transaction.
     * It automatically prefixes the summary with "[+ In]" for positive amounts
     * or "[- Out]" for negative amounts to easily distinguish the transaction flow.
     *
     * @return A string detailing the transaction flow direction, amount, timestamp, and description.
     */
    @Override
    public String getInfo() {
        String type = (amount >= 0) ? "[+ In]" : "[- Out]";
        return type + " Amount: " + amount + " | Time: " + createdAt + " | Desc: " + description;
    }
}