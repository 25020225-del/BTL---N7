package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import static utils.ConsoleColors.*;

/**
 * Represents an auction session in the system.
 * Manages the item details, seller, current bidding state, bid history,
 * and automated bot queue (AutoBids).
 */
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

    // PriorityQueue to guarantee that auto-bids are processed based on their registration time
    private PriorityQueue<AutoBid> activeAutoBids;
    private LocalDateTime startTime;

    /**
     * Default constructor.
     * Initializes the auto-bid queue with a time-based comparator.
     */
    public Auction() {
        super();
        this.activeAutoBids = new PriorityQueue<>(Comparator.comparing(AutoBid::getTimeRegistered));
    }

    /**
     * Full constructor to initialize a new auction session.
     *
     * @param id           The unique identifier of the auction.
     * @param item         The item being auctioned.
     * @param seller       The user selling the item.
     * @param bidIncrement The minimum increment required for a new bid.
     * @param startTime    The starting time of the auction.
     * @param endTime      The scheduled ending time of the auction.
     */
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

        // Priority queue sorted by earliest registration time first
        this.activeAutoBids = new PriorityQueue<>(Comparator.comparing(AutoBid::getTimeRegistered));

        if (seller.isGood()) {
            this.status = STATUS_OPEN;
        } else {
            this.status = STATUS_PENDING;
        }
    }

    /**
     * Factory method to generate a new Auction instance with calculated start and end times.
     */
    public static Auction createNewAuction(Item item, Seller seller, double bidIncrement, int durationMinutes) {
        String newId = "AUC-" + System.currentTimeMillis();
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusMinutes(durationMinutes);

        Auction newAuction = new Auction(newId, item, seller, bidIncrement, start, end);

        if (seller.isGood()) {
            newAuction.setStatus(STATUS_OPEN);
        } else {
            newAuction.setStatus(STATUS_PENDING);
        }

        return newAuction;
    }

    // --- GETTERS & SETTERS ---

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

    public PriorityQueue<AutoBid> getActiveAutoBids() { return activeAutoBids; }

    // --- BUSINESS LOGIC METHODS ---

    /**
     * Core logic to process a bid. Evaluates validity, manages outbidding,
     * and implements anti-sniping mechanisms.
     *
     * @param bidder    The user placing the bid.
     * @param newMaxBid The bid amount.
     * @return true if the bid is valid and successfully placed.
     */
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

        // Logic for handling the winning bidder and outbidding
        if (winningBidder == null) {
            currentPrice = item.getStartingPrice();
            highestMaxBid = newMaxBid;
            winningBidder = bidder;

        } else if (bidder.getId().equals(winningBidder.getId())) {
            // User is increasing their own maximum bid limit
            if (newMaxBid > highestMaxBid) {
                highestMaxBid = newMaxBid;
            }
        } else {
            // User is trying to outbid the current winner
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

        // Anti-Sniping Algorithm: Extend time by 2 minutes if a bid is placed in the last minute
        if (LocalDateTime.now().plusMinutes(1).isAfter(endTime)) {
            endTime = endTime.plusMinutes(2);
            System.out.println(YELLOW + "[System]: Time increased 2 minutes (Anti-sniping triggered)" + RESET);
        }

        return true;
    }

    /**
     * Checks if the auction duration has passed and transitions the state appropriately.
     */
    public synchronized void closeAuctionIfTimeIsUp() {
        if (this.status.equals(STATUS_RUNNING) && LocalDateTime.now().isAfter(this.endTime)) {
            if (this.winningBidder != null) {
                this.status = STATUS_FINISHED;
                System.out.println(GREEN + "[System]: Auction session \"" + this.getId() + "\" has ended" + RESET);
                System.out.println(GREEN + "[System]: Winner: \"" + winningBidder.getUserName() + "\" at VND " + currentPrice + RESET);
            } else {
                this.status = STATUS_CANCELED;
                System.out.println(YELLOW + "[System]: Auction session \"" + this.getId() + "\" was cancelled due to no bidders" + RESET);
            }
        }
    }

    /**
     * Registers a new auto-bid bot into the auction's priority queue.
     *
     * @param bidder        The owner of the bot.
     * @param maxBid        The absolute maximum limit for this bot.
     * @param userIncrement The step amount to increase when outbidding competitors.
     * @return true if successfully registered.
     */
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

        // PriorityQueue automatically sorts the elements based on the Comparator (registration time)
        activeAutoBids.offer(newAutoBid);

        System.out.println(BLUE + "[Auto-Bid]: \"" + bidder.getUserName() + "\" registered Auto-Bid successfully (Max: " + maxBid + ")" + RESET);

        return true;
    }

    @Override
    public String getInfo() {
        return "=== AUCTION INFORMATION ===\n" +
                "Auction ID: " + this.getId() + "\n" +
                "Item: " + (item != null ? item.getItemName() : "N/A") + "\n" +
                "Current Price: VND " + this.currentPrice + "\n" +
                "Leading Bidder: " + (winningBidder != null ? winningBidder.getUserName() : "None") + "\n" +
                "Status: " + this.status;
    }
}