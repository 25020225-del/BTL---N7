package model;

import java.time.LocalDateTime;

public class BidTransaction extends Entity {
    private Bidder bidder;
    private double bidAmount;
    private LocalDateTime timestamp;

    public BidTransaction(String id, Bidder bidder, double bidAmount) {
        super(id);
        this.bidder = bidder;
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now();
    }

    public Bidder getBidder() { return bidder; }
    public double getBidAmount() { return bidAmount; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String getInfo() {
        return "[Transaction] Bidder: " + bidder.getUserName() + " placed VND" + bidAmount + " at " + timestamp;
    }
}