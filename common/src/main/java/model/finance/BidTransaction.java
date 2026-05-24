package model.finance;

import model.base.Entity;
import model.user.User;

import java.time.LocalDateTime;

/**
 * Domain entity capturing a historical immutable ledger record of a specific bid placement.
 */
public class BidTransaction extends Entity {

    private User bidder;
    private long bidAmount;
    private LocalDateTime timestamp;

    public BidTransaction() {
        super();
    }

    /**
     * Primary constructor initializing a complete audited bidding ledger event.
     *
     * @param id        unique primary key token mapping the transaction
     * @param bidder    the authenticated user profile committing the bid
     * @param bidAmount total monetary value threshold offered
     */
    public BidTransaction(String id, User bidder, long bidAmount) {
        super(id);
        this.bidder = bidder;
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now();
    }

    public User getBidder() { return bidder; }
    public void setBidder(User bidder) { this.bidder = bidder; }
    public long getBidAmount() { return bidAmount; }
    public void setBidAmount(long bidAmount) { this.bidAmount = bidAmount; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    @Override
    public String getInfo() {
        return "[Bid Event] Bidder: " + (bidder != null ? bidder.getUserName() : "Unknown")
                + " | Amount: " + bidAmount + " | Time: " + timestamp;
    }
}