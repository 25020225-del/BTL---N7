package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static utils.ConsoleColors.*;

public class Auction extends Entity {

    public static final String STATUS_PENDING  = "PENDING_APPROVAL";
    public static final String STATUS_OPEN     = "OPEN";
    public static final String STATUS_RUNNING  = "RUNNING";
    public static final String STATUS_FINISHED = "FINISHED";
    public static final String STATUS_PAID     = "PAID";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_DELETED  = "DELETED";

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
        this.activeAutoBids = new ArrayList<>();
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

        if (seller.isGood()) {
            this.status = STATUS_OPEN;
        } else {
            this.status = STATUS_PENDING;
        }
    }

    // Các hàm Getter / Setter giữ nguyên
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

    // THAY ĐỔI: Trả về danh sách các giao dịch (Transactions) được tạo ra
    public synchronized List<BidTransaction> placeBid(Bidder bidder, double newMaxBid) {
        if (status.equals(STATUS_DELETED) || !status.equals(STATUS_RUNNING) || LocalDateTime.now().isAfter(endTime)) {
            System.out.println("[Error]: " + RED + "Cannot place a bid right now." + RESET);
            return null; // Trả về null thay vì false
        }
        if (newMaxBid < 0) return null;

        double minRequiredBid = (winningBidder == null) ? currentPrice : (currentPrice + bidIncrement);
        if (newMaxBid < minRequiredBid) {
            System.out.println("[Error]: " + RED + "Bid must be greater than or equal to VND " + minRequiredBid + RESET);
            return null;
        }

        if (winningBidder == null) {
            currentPrice = item.getStartingPrice();
            highestMaxBid = newMaxBid;
            winningBidder = bidder;
        } else if (bidder.getId().equals(winningBidder.getId())) {
            if (newMaxBid > highestMaxBid) highestMaxBid = newMaxBid;
        } else {
            if (newMaxBid > highestMaxBid) {
                currentPrice = highestMaxBid + bidIncrement;
                if (currentPrice > newMaxBid) currentPrice = newMaxBid;
                highestMaxBid = newMaxBid;
                winningBidder = bidder;
            } else {
                currentPrice = newMaxBid + bidIncrement;
                if (currentPrice > highestMaxBid) currentPrice = highestMaxBid;
            }
        }

        List<BidTransaction> generatedTxns = new ArrayList<>();

        // 1. Ghi nhận giao dịch của người thật
        BidTransaction transaction = new BidTransaction("TXN-" + System.currentTimeMillis(), bidder, currentPrice);
        bidHistory.add(transaction);
        generatedTxns.add(transaction);

        // 2. Kích hoạt AutoBids nhảy vào đấu lại (Thu thập luôn giao dịch của bot)
        resolveAutoBids(generatedTxns);

        // 3. Chống bắn tỉa
        if (LocalDateTime.now().plusMinutes(1).isAfter(endTime)) {
            endTime = endTime.plusMinutes(2);
            System.out.println(YELLOW + "[System]: Time increased 2 minutes (Anti-sniping triggered)" + RESET);
        }

        return generatedTxns;
    }

    public synchronized String closeAuctionIfTimeIsUp() {
        if (this.status.equals(STATUS_RUNNING) && LocalDateTime.now().isAfter(this.endTime)) {
            if (this.winningBidder != null) {
                this.status = STATUS_FINISHED;
                System.out.println(GREEN + "[System]: Auction session \"" + this.id + "\" has ended. Winner: " + winningBidder.getUserName() + RESET);
                return STATUS_FINISHED;
            } else {
                this.status = STATUS_CANCELED;
                System.out.println(YELLOW + "[System]: Auction session \"" + this.id + "\" was cancelled due to no bidders" + RESET);
                return STATUS_CANCELED;
            }
        }
        return null;
    }

    // THAY ĐỔI: Trả về danh sách giao dịch
    public synchronized List<BidTransaction> registerAutoBid(Bidder bidder, double maxBid, double userIncrement) {
        if (!status.equals(STATUS_RUNNING) || maxBid <= currentPrice) {
            System.out.println("[Error]: " + RED + "Invalid AutoBid configuration" + RESET);
            return null;
        }

        AutoBid newAutoBid = new AutoBid(bidder, maxBid, userIncrement);
        activeAutoBids.add(newAutoBid);
        activeAutoBids.sort((b1, b2) -> b1.getTimeRegistered().compareTo(b2.getTimeRegistered()));

        System.out.println(BLUE + "[Auto-Bid]: \"" + bidder.getUserName() + "\" registered Auto-Bid successfully (Max: " + maxBid + ")" + RESET);

        List<BidTransaction> generatedTxns = new ArrayList<>();
        resolveAutoBids(generatedTxns);
        return generatedTxns;
    }

    private synchronized void resolveAutoBids(List<BidTransaction> generatedTxns) {
        boolean isPriceChanged;
        do {
            isPriceChanged = false;
            for (AutoBid bot : activeAutoBids) {
                if (winningBidder != null && bot.getBidder().getId().equals(winningBidder.getId())) {
                    continue;
                }

                double requiredPrice = (winningBidder == null) ? item.getStartingPrice() : currentPrice + bot.getIncrement();

                if (requiredPrice <= bot.getMaxBid()) {
                    currentPrice  = requiredPrice;
                    winningBidder = bot.getBidder();

                    // Sleep siêu nhỏ để tránh ID trùng lặp 100% trong DB
                    try { Thread.sleep(1); } catch (Exception ignored) {}

                    BidTransaction txn = new BidTransaction("AUTO-" + System.currentTimeMillis(), winningBidder, currentPrice);
                    bidHistory.add(txn);
                    generatedTxns.add(txn);

                    System.out.println(BLUE + "[Auto-Bid]: \"" + winningBidder.getUserName() + "\" automatically raised the bid to: " + currentPrice + RESET);
                    isPriceChanged = true;
                    break;
                }
            }
        } while (isPriceChanged);
    }

    @Override
    public String getInfo() {
        return "=== AUCTION INFORMATION ===\n" +
                "Auction ID: " + this.id + "\n" +
                "Item: " + (item != null ? item.getItemName() : "N/A") + "\n" +
                "Current Price: VND " + this.currentPrice + "\n" +
                "Leading Bidder: " + (winningBidder != null ? winningBidder.getUserName() : "None") + "\n" +
                "Status: " + this.status;
    }
}