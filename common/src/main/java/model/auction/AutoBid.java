package model.auction;

import model.user.User;
import java.time.LocalDateTime;

/**
 * Domain entity mapping runtime configuration parameters for automated proxy bidding agents.
 */
public class AutoBid {

    private User bidder;
    private long maxBid;
    private long increment;
    private LocalDateTime timeRegistered;

    public AutoBid() {
    }

    /**
     * Instantiates a validated proxy automated agent configuration tracking registration chronological offsets.
     *
     * @param bidder    the account entity owning the configuration profile
     * @param maxBid    the absolute ceiling valuation caps allowed for bids
     * @param increment minimum outbid delta step applied on competition triggers
     */
    public AutoBid(User bidder, long maxBid, long increment) {
        this.bidder = bidder;
        this.maxBid = maxBid;
        this.increment = increment;
        this.timeRegistered = LocalDateTime.now();
    }

    public User getBidder() { return bidder; }
    public void setBidder(User bidder) { this.bidder = bidder; }
    public long getMaxBid() { return maxBid; }
    public void setMaxBid(long maxBid) { this.maxBid = maxBid; }
    public long getIncrement() { return increment; }
    public void setIncrement(long increment) { this.increment = increment; }
    public LocalDateTime getTimeRegistered() { return timeRegistered; }
    public void setTimeRegistered(LocalDateTime timeRegistered) { this.timeRegistered = timeRegistered; }
}