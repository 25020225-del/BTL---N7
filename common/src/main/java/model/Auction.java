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

    public synchronized boolean placeBid(Bidder bidder, double newMaxBid) {
        if (status.equals(STATUS_DELETED)) {
            System.out.println("[Error]: " + RED + "The auction session has been deleted by Admin" + RESET);
            return false;
        }

        if (!status.equals(STATUS_RUNNING) || LocalDateTime.now().isAfter(endTime)) {
            System.out.println("[Error]: " + RED + "Cannot place a bid. The auction is not running or has already ended" + RESET);
            return false;
        }

        if (newMaxBid < 0) {
            System.out.println("[Error]: " + RED + "Invalid Bid" + RESET);
            return false;
        }

        double minRequiredBid = (winningBidder == null) ? currentPrice : (currentPrice + bidIncrement);
        if (newMaxBid < minRequiredBid) {
            System.out.println("[Error]: " + RED + "Bid must be greater than or equal to VND " + minRequiredBid + RESET);
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

        BidTransaction transaction = new BidTransaction("TXN-" + System.currentTimeMillis(), bidder, currentPrice);
        bidHistory.add(transaction);

        if (LocalDateTime.now().plusMinutes(1).isAfter(endTime)) {
            endTime = endTime.plusMinutes(2);
            System.out.println(YELLOW + "[System]: Time increased 2 minutes (Anti-sniping triggered)" + RESET);
        }

        return true;
    }

    public synchronized void closeAuctionIfTimeIsUp() {
        if (this.status.equals(STATUS_RUNNING) && LocalDateTime.now().isAfter(this.endTime)) {
            if (this.winningBidder != null) {
                this.status = STATUS_FINISHED;
                System.out.println(GREEN + "[System]: Auction session \"" + this.id + "\" has ended" + RESET);
                System.out.println(GREEN + "[System]: Winner: \"" + winningBidder.getUserName() + "\" at VND " + currentPrice + RESET);
            } else {
                this.status = STATUS_CANCELED;
                System.out.println(YELLOW + "[System]: Auction session \"" + this.id + "\" was cancelled due to no bidders" + RESET);
            }
        }
    }

    public synchronized boolean registerAutoBid(Bidder bidder, double maxBid, double userIncrement) {
        if (!status.equals(STATUS_RUNNING)) {
            System.out.println("[Error]: " + RED + "Auction is not in RUNNING status" + RESET);
            return false;
        }

        if (maxBid <= currentPrice) {
            System.out.println("[Error]: " + RED + "Maximum bid must be greater than current price" + RESET);
            return false;
        }

        AutoBid newAutoBid = new AutoBid(bidder, maxBid, userIncrement);
        activeAutoBids.add(newAutoBid);

        activeAutoBids.sort((b1, b2) -> b1.getTimeRegistered().compareTo(b2.getTimeRegistered()));

        System.out.println(BLUE + "[Auto-Bid]: \"" + bidder.getUserName() + "\" registered Auto-Bid successfully (Max: " + maxBid + ")" + RESET);

        resolveAutoBids();

        return true;
    }

    private synchronized void resolveAutoBids() {
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

                    BidTransaction txn = new BidTransaction("AUTO-" + System.currentTimeMillis(), winningBidder, currentPrice);
                    bidHistory.add(txn);

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