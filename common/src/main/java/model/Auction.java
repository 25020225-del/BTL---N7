package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Auction extends Entity {
    private Item item;
    private Seller seller;

    private double currentPrice;
    private double highestMaxBid;
    private double bidIncrement;

    private Bidder winningBidder;
    private String status;
    private LocalDateTime endTime;
    private List<BidTransaction> bidHistory;
    private LocalDateTime startTime;

    public Auction() {
        super();
    }

    public Auction(String id, Item item, Seller seller, double bidIncrement,LocalDateTime startTime, LocalDateTime endTime) {
        super(id);
        this.item = item;
        this.seller = seller;
        this.currentPrice = item.getStartingPrice();
        this.highestMaxBid = 0;
        this.bidIncrement = bidIncrement;
        this.status = "OPEN";
        this.startTime = startTime;
        this.endTime = endTime;
        this.bidHistory = new ArrayList<>();
    }

    // --- GETTER VÀ SETTER (Đã chuẩn, giữ nguyên) ---
    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }

    public Seller getSeller() { return seller; }
    public void setSeller(Seller seller) { this.seller = seller; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getHighestMaxBid() { return highestMaxBid; }
    public void setHighestMaxBid(double highestMaxBid) { this.highestMaxBid = highestMaxBid; }

    public double getBidIncrement() { return bidIncrement; }
    public void setBidIncrement(double bidIncrement) { this.bidIncrement = bidIncrement; }

    public Bidder getWinningBidder() { return winningBidder; }
    public void setWinningBidder(Bidder winningBidder) { this.winningBidder = winningBidder; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public List<BidTransaction> getBidHistory() { return bidHistory; }
    public void setBidHistory(List<BidTransaction> bidHistory) { this.bidHistory = bidHistory; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }


    // --- HÀM NGHIỆP VỤ ---
    public synchronized boolean placeBid(Bidder bidder, double newMaxBid) {

        // 1. Kiểm tra tính hợp lệ cơ bản nhất
        if (newMaxBid < 0) {
            System.out.println("Invalid Bid");
            return false;
        }

        if (!status.equals("RUNNING") || LocalDateTime.now().isAfter(endTime)) {
            System.out.println("The Auction has closed!");
            return false;
        }

        // 2. Tối ưu logic: Nếu là người đầu tiên bóc tem thì được đặt bằng giá khởi điểm.
        // Nếu đã có người đặt rồi thì bắt buộc phải cao hơn giá hiện tại + bước giá.
        double minRequiredBid = (winningBidder == null) ? currentPrice : (currentPrice + bidIncrement);
        if (newMaxBid < minRequiredBid) {
            System.out.println("Bid must be greater than or equal to VND " + minRequiredBid);
            return false;
        }

        // 3. Logic Proxy Bidding (eBay style)
        if (winningBidder == null) {
            currentPrice = item.getStartingPrice();
            highestMaxBid = newMaxBid;
            winningBidder = bidder;

        } else if (bidder.getId().equals(winningBidder.getId())) {
            if (newMaxBid > highestMaxBid) {
                highestMaxBid = newMaxBid;
            }
        } else {
            if (newMaxBid > highestMaxBid) {
                currentPrice = highestMaxBid + bidIncrement;
                if (currentPrice > newMaxBid) {
                    currentPrice = newMaxBid;
                }
                highestMaxBid = newMaxBid;
                winningBidder = bidder;

            } else {
                currentPrice = newMaxBid + bidIncrement;
                if (currentPrice > highestMaxBid) {
                    currentPrice = highestMaxBid;
                }
            }
        }

        // 4. Ghi nhận lịch sử
        BidTransaction transaction = new BidTransaction("TXN-" + System.currentTimeMillis(), bidder, currentPrice);
        bidHistory.add(transaction);

        // 5. Anti-sniping
        if (LocalDateTime.now().plusMinutes(1).isAfter(endTime)) {
            endTime = endTime.plusMinutes(2);
            System.out.println("Time increased 2 minutes!");
        }

        return true;
    }

    @Override
    public String getInfo() {
        return "=== THÔNG TIN PHIÊN ĐẤU GIÁ ===\n" +
                "ID Phiên: " + this.id + "\n" +
                "Sản phẩm: " + (item != null ? item.getItemName() : "N/A") + "\n" +
                "Giá hiện tại: VND " + this.currentPrice + "\n" +
                "Người dẫn đầu: " + (winningBidder != null ? winningBidder.getUserName() : "Chưa có ai") + "\n" +
                "Trạng thái: " + this.status;
    }
}