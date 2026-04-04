package model;

import java.time.LocalDateTime;

public class AutoBid {
    private Bidder bidder;
    private double maxBid;
    private double increment; // Bước giá do user tự đặt
    private LocalDateTime timeRegistered; // Thời điểm đăng ký để xét ưu tiên

    public AutoBid() {
        super();
    }

    public AutoBid(Bidder bidder, double maxBid, double increment) {
        this.bidder = bidder;
        this.maxBid = maxBid;
        this.increment = increment;
        this.timeRegistered = LocalDateTime.now();
    }

    // --- GETTER VÀ SETTER (Bắt buộc cho JSON) ---
    public Bidder getBidder() { return bidder; }
    public void setBidder(Bidder bidder) { this.bidder = bidder; }

    public double getMaxBid() { return maxBid; }
    public void setMaxBid(double maxBid) { this.maxBid = maxBid; }

    public double getIncrement() { return increment; }
    public void setIncrement(double increment) { this.increment = increment; }

    public LocalDateTime getTimeRegistered() { return timeRegistered; }
    public void setTimeRegistered(LocalDateTime timeRegistered) { this.timeRegistered = timeRegistered; }
}