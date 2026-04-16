package model;

import java.time.LocalDateTime;

public class BidTransaction extends Entity {

    private Bidder bidder;
    private double bidAmount;
    private LocalDateTime timestamp;

    public BidTransaction() {
        super();
    }

    public BidTransaction(String id, Bidder bidder, double bidAmount) {
        super(id);
        this.bidder    = bidder;
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now();
    }

    public Bidder getBidder() { return bidder; }
    public void setBidder(Bidder bidder) { this.bidder = bidder; }

    public double getBidAmount() { return bidAmount; }
    public void setBidAmount(double bidAmount) { this.bidAmount = bidAmount; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    @Override
    public String getInfo() {
        String name = (bidder != null) ? bidder.getUserName() : "Unknown";
        return "[Transaction]: Bidder \"" + name + "\" placed VND " + bidAmount + " at " + timestamp;
    }

}