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
        this.bidder = bidder;
        this.bidAmount = bidAmount;
        this.timestamp = LocalDateTime.now();
    }

    // --- GETTER (Lấy dữ liệu ra) ---
    public Bidder getBidder() { return bidder; }
    public double getBidAmount() { return bidAmount; }
    public LocalDateTime getTimestamp() { return timestamp; }

    // 2. THÊM SETTER (Bắt buộc để thư viện JSON ghi đè dữ liệu vào)
    public void setBidder(Bidder bidder) { this.bidder = bidder; }
    public void setBidAmount(double bidAmount) { this.bidAmount = bidAmount; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    @Override
    public String getInfo() {
        // Mẹo nhỏ: Thêm kiểm tra bidder != null để phòng hờ trường hợp
        // mạng bị lag, mất dữ liệu người dùng thì giao diện cũng không bị sập (lỗi NullPointerException)
        String name = (bidder != null) ? bidder.getUserName() : "Unknown";
        return "[Transaction] Bidder: " + name + " placed VND " + bidAmount + " at " + timestamp;
    }
}