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
    private List<AutoBid> activeAutoBids;
    private LocalDateTime startTime;

    public Auction() {
        super();
        this.activeAutoBids = new ArrayList<>(); // Khởi tạo túi rỗng
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
        this.activeAutoBids = new ArrayList<>();

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
    // ==========================================
    // TÍNH NĂNG AUTO-BIDDING (Yêu cầu 3.2.1)
    // ==========================================

    // 1. Client gọi hàm này để đăng ký Auto-Bid
    public synchronized boolean registerAutoBid(Bidder bidder, double maxBid, double userIncrement) {
        if (!status.equals(STATUS_RUNNING)) {
            System.out.println("Lỗi: Phiên đấu giá không trong trạng thái mở!");
            return false;
        }

        if (maxBid <= currentPrice) {
            System.out.println("Lỗi: Giá tối đa (maxBid) phải lớn hơn giá hiện tại!");
            return false;
        }

        // Tạo bot và nhét vào danh sách
        AutoBid newAutoBid = new AutoBid(bidder, maxBid, userIncrement);
        activeAutoBids.add(newAutoBid);

        // Sắp xếp lại danh sách ưu tiên người đăng ký trước (Giải quyết gạch đầu dòng thứ 3 của đề)
        activeAutoBids.sort((b1, b2) -> b1.getTimeRegistered().compareTo(b2.getTimeRegistered()));

        System.out.println(bidder.getUserName() + " đã đăng ký Auto-Bid thành công (Max: " + maxBid + ")");

        // Ngay khi có Auto-Bid mới, kích hoạt chiến trường để các bot tự đấu với nhau
        resolveAutoBids();

        return true;
    }

    // 2. Thuật toán cho các bot tự "đấm" nhau
    private synchronized void resolveAutoBids() {
        boolean isPriceChanged;

        // Vòng lặp do-while này sẽ chạy liên tục cho đến khi không còn bot nào
        // có khả năng trả giá cao hơn người đang dẫn đầu.
        do {
            isPriceChanged = false;

            for (AutoBid bot : activeAutoBids) {
                // Nếu bot này đại diện cho người đang dẫn đầu thì bỏ qua
                if (winningBidder != null && bot.getBidder().getId().equals(winningBidder.getId())) {
                    continue;
                }

                // Tính toán giá cần thiết để giành Top 1 (dùng bước giá riêng của bot đó)
                double requiredPrice = (winningBidder == null) ? item.getStartingPrice() : currentPrice + bot.getIncrement();

                // Nếu giá cần thiết vẫn nằm trong khả năng chịu đựng của bot (<= maxBid)
                if (requiredPrice <= bot.getMaxBid()) {

                    currentPrice = requiredPrice;
                    winningBidder = bot.getBidder();

                    // Ghi vào lịch sử
                    BidTransaction txn = new BidTransaction("AUTO-" + System.currentTimeMillis(), winningBidder, currentPrice);
                    bidHistory.add(txn);

                    System.out.println("[Auto-Bid] " + winningBidder.getUserName() + " tự động nâng giá lên: " + currentPrice);

                    // Đánh dấu là có sự thay đổi giá, phá vỡ vòng for hiện tại
                    // để bắt đầu xét lại từ đầu (đảm bảo luật ưu tiên người đăng ký trước)
                    isPriceChanged = true;
                    break;
                }
            }
        } while (isPriceChanged);
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