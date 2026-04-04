package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Auction extends Entity {

    public static final String STATUS_PENDING = "PENDING_APPROVAL"; // Chờ duyệt (Giữ lại để Admin làm việc)
    public static final String STATUS_OPEN = "OPEN";               // Đã duyệt, chờ đến giờ bắt đầu
    public static final String STATUS_RUNNING = "RUNNING";         // Đang diễn ra
    public static final String STATUS_FINISHED = "FINISHED";       // Đã kết thúc (Thay cho chữ CLOSED cũ)
    public static final String STATUS_PAID = "PAID";               // Người thắng đã thanh toán
    public static final String STATUS_CANCELED = "CANCELED";       // Bị hủy (Do vi phạm hoặc không ai mua)
    public static final String STATUS_DELETED = "DELETED";         // Admin xóa

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

    public Auction(String id, Item item, Seller seller, double bidIncrement, LocalDateTime startTime, LocalDateTime endTime) {
        super(id);
        this.item = item;
        this.seller = seller;
        this.currentPrice = item.getStartingPrice();
        this.highestMaxBid = 0;
        this.bidIncrement = bidIncrement;
        this.startTime = startTime;
        this.endTime = endTime;
        this.bidHistory = new ArrayList<>();

        // Đã xóa dòng this.status = "OPEN" thừa thãi ở đây
        if (seller.isGood()) {
            this.status = STATUS_OPEN; // Theo đúng yêu cầu: Mở đầu bằng OPEN
        } else {
            this.status = STATUS_PENDING;
        }
    }

    // --- GETTER VÀ SETTER ---
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

        // 1. Kiểm tra xem phiên đấu giá có bị Admin xóa không
        if (status.equals(STATUS_DELETED)) {
            System.out.println("Error: The auction session has been deleted by Admin!");
            return false;
        }

        // 2. Gộp kiểm tra trạng thái RUNNING và thời gian kết thúc
        if (!status.equals(STATUS_RUNNING) || LocalDateTime.now().isAfter(endTime)) {
            System.out.println("Cannot place a bid: The auction is not running or has already ended!");
            return false;
        }

        // 3. Kiểm tra tính hợp lệ cơ bản của số tiền
        if (newMaxBid < 0) {
            System.out.println("Invalid Bid");
            return false;
        }

        // 4. Logic Proxy Bidding (eBay style)
        double minRequiredBid = (winningBidder == null) ? currentPrice : (currentPrice + bidIncrement);
        if (newMaxBid < minRequiredBid) {
            System.out.println("Bid must be greater than or equal to VND " + minRequiredBid);
            return false;
        }

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

        // 5. Ghi nhận lịch sử
        BidTransaction transaction = new BidTransaction("TXN-" + System.currentTimeMillis(), bidder, currentPrice);
        bidHistory.add(transaction);

        // 6. Anti-sniping
        if (LocalDateTime.now().plusMinutes(1).isAfter(endTime)) {
            endTime = endTime.plusMinutes(2);
            System.out.println("Time increased 2 minutes!");
        }

        return true;
    }

    public synchronized void closeAuctionIfTimeIsUp() {
        // Nếu phiên đang chạy và thời gian hiện tại đã vượt quá thời gian kết thúc
        if (this.status.equals(STATUS_RUNNING) && LocalDateTime.now().isAfter(this.endTime)) {

            if (this.winningBidder != null) {
                // Kịch bản 1: Có người thắng cuộc
                this.status = STATUS_FINISHED; // Chuyển sang FINISHED
                System.out.println("Auction session " + this.id + " has ended");
                System.out.println("Winner: " + winningBidder.getUserName() + " at VND price " + currentPrice);
            } else {
                // Kịch bản 2: Ế, không có ai đặt giá
                this.status = STATUS_CANCELED; // Chuyển sang CANCELED
                System.out.println("Auction session " + this.id + " but there were no bidders (Cancelled).");
            }
        }
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