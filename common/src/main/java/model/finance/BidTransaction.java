package model.finance;

import model.base.Entity;
import model.user.User;
import java.time.LocalDateTime;

/**
 * Represents a historical record of a bid placed within an auction session.
 * This entity tracks the user who placed the bid, the amount offered,
 * and the exact timestamp of the transaction for auditing and history tracking.
 */
public class BidTransaction extends Entity {

    private User bidder;
    private double bidAmount;
    private LocalDateTime timestamp;

    /**
     * Default constructor.
     */
    public BidTransaction() {
        super();
    }

    /**
     * Constructs a new BidTransaction and automatically records the current timestamp.
     *
     * @param id        The unique identifier for this bid transaction.
     * @param bidder    The user who placed the bid.
     * @param bidAmount The monetary value of the bid.
     */
    public BidTransaction(String id, User bidder, double bidAmount) {
        super(id);
        this.bidder    = bidder;
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now();
    }

    public User getBidder() { return bidder; }
    public void setBidder(User bidder) { this.bidder = bidder; }

    public double getBidAmount() { return bidAmount; }
    public void setBidAmount(double bidAmount) { this.bidAmount = bidAmount; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    /**
     * Generates a formatted summary string of this bid transaction.
     *
     * @return A string containing the bidder's username, the bid amount, and the timestamp.
     */
    @Override
    public String getInfo() {
        String name = (bidder != null) ? bidder.getUserName() : "Unknown";
        return "[Transaction]: User \"" + name + "\" placed VND " + bidAmount + " at " + timestamp;
    }
}