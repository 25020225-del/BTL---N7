package model.auction;

import model.user.User;

import java.time.LocalDateTime;

/**
 * Represents an automated bidding configuration (bot) for a specific user in an auction.
 * It stores the user's maximum budget, the step increment for outbidding competitors,
 * and the exact registration time used for prioritization in the auto-bid queue.
 */
public class AutoBid {

    private User bidder;
    private double maxBid;
    private double increment;
    private LocalDateTime timeRegistered;

    /**
     * Default constructor.
     */
    public AutoBid() {
        super();
    }

    /**
     * Constructs a new automated bidding configuration and automatically sets the registration time.
     *
     * @param bidder    The user who owns this auto-bid configuration.
     * @param maxBid    The absolute maximum amount the user is willing to spend.
     * @param increment The amount to increase the bid by when outbidding others.
     */
    public AutoBid(User bidder, double maxBid, double increment) {
        this.bidder = bidder;
        this.maxBid = maxBid;
        this.increment = increment;
        this.timeRegistered = LocalDateTime.now();
    }

    public User getBidder() {
        return bidder;
    }

    public void setBidder(User bidder) {
        this.bidder = bidder;
    }

    public double getMaxBid() {
        return maxBid;
    }

    public void setMaxBid(double maxBid) {
        this.maxBid = maxBid;
    }

    public double getIncrement() {
        return increment;
    }

    public void setIncrement(double increment) {
        this.increment = increment;
    }

    public LocalDateTime getTimeRegistered() {
        return timeRegistered;
    }

    public void setTimeRegistered(LocalDateTime timeRegistered) {
        this.timeRegistered = timeRegistered;
    }
}