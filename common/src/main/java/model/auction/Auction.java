package model.auction;

import model.base.Entity;
import model.finance.BidTransaction;
import model.item.Item;
import model.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import static utils.ConsoleColors.*;

/**
 * Represents an auction session within the system.
 * This class manages the lifecycle of an auction, including the item being sold,
 * the current price, manual bidding history, automated bots (AutoBids), and time tracking.
 */
public class Auction extends Entity {

    // Auction lifecycle states
    public static final String STATUS_PENDING = "PENDING_APPROVAL";
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_FINISHED = "FINISHED";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_DELETED = "DELETED";

    private Item item;
    private User seller;

    private double currentPrice;
    private double highestMaxBid;
    private double bidIncrement;

    private User winningBidder;
    private String status;
    private LocalDateTime endTime;
    private LocalDateTime maxEndTime; // Hard-cap limit for Anti-Sniping
    private List<BidTransaction> bidHistory;

    // PriorityQueue to guarantee that auto-bids are processed based on their registration time
    private PriorityQueue<AutoBid> activeAutoBids;
    private LocalDateTime startTime;

    /**
     * Default constructor.
     * Initializes the auto-bid queue with a time-based comparator to prioritize earlier registrations.
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
    public Auction(String id, Item item, User seller, double bidIncrement, LocalDateTime startTime, LocalDateTime endTime) {
        super(id);
        this.item = item;
        this.seller = seller;
        this.currentPrice = item.getStartingPrice();
        this.highestMaxBid = 0;
        this.bidIncrement = bidIncrement;
        this.startTime = startTime;
        this.endTime = endTime;

        // Anti-Sniping hard-cap: maximum 30 minutes extension from the initial end time
        this.maxEndTime = endTime.plusMinutes(30);

        this.bidHistory = new ArrayList<>();

        this.activeAutoBids = new PriorityQueue<>(Comparator.comparing(AutoBid::getTimeRegistered));

        if (seller.isGood()) {
            this.status = STATUS_OPEN;
        } else {
            this.status = STATUS_PENDING;
        }
    }

    /**
     * Factory method to generate a new Auction instance with dynamically calculated start and end times.
     *
     * @param item            The item to be auctioned.
     * @param seller          The user hosting the auction.
     * @param bidIncrement    The required minimum increment between bids.
     * @param durationMinutes The total active duration of the auction in minutes.
     * @return A newly initialized Auction instance.
     */
    public static Auction createNewAuction(Item item, User seller, double bidIncrement, LocalDateTime startTime, int durationMinutes) {
        String newId = "AUC-" + System.currentTimeMillis();
        LocalDateTime end = startTime.plusMinutes(durationMinutes);

        Auction newAuction = new Auction(newId, item, seller, bidIncrement, startTime, end);

        if (seller.isGood()) {
            newAuction.setStatus(STATUS_OPEN);
        } else {
            newAuction.setStatus(STATUS_PENDING);
        }

        return newAuction;
    }

    // --- GETTERS & SETTERS ---

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public User getSeller() {
        return seller;
    }

    public void setSeller(User seller) {
        this.seller = seller;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public double getHighestMaxBid() {
        return highestMaxBid;
    }

    public void setHighestMaxBid(double highestMaxBid) {
        this.highestMaxBid = highestMaxBid;
    }

    public double getBidIncrement() {
        return bidIncrement;
    }

    public void setBidIncrement(double bidIncrement) {
        this.bidIncrement = bidIncrement;
    }

    public User getWinningBidder() {
        return winningBidder;
    }

    public void setWinningBidder(User winningBidder) {
        this.winningBidder = winningBidder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
    
    public LocalDateTime getMaxEndTime() {
        return maxEndTime;
    }
    
    public void setMaxEndTime(LocalDateTime maxEndTime) {
        this.maxEndTime = maxEndTime;
    }

    public LocalDateTime getMaxEndTime() {
        return maxEndTime;
    }

    public void setMaxEndTime(LocalDateTime maxEndTime) {
        this.maxEndTime = maxEndTime;
    }

    public List<BidTransaction> getBidHistory() {
        return bidHistory;
    }

    public void setBidHistory(List<BidTransaction> bidHistory) {
        this.bidHistory = bidHistory;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public PriorityQueue<AutoBid> getActiveAutoBids() {
        return activeAutoBids;
    }

    // --- BUSINESS LOGIC METHODS ---

    /**
     * Processes a manual bid placed by a user.
     * Evaluates constraints, resolves outbidding logic, and applies anti-sniping mechanisms
     * (extending the auction time if a bid is placed near the deadline).
     *
     * @param bidder    The user placing the bid.
     * @param newMaxBid The maximum amount the user is willing to bid.
     * @return The created {@link BidTransaction} if the bid is valid and successfully placed; {@code null} otherwise.
     */
    public synchronized BidTransaction placeBid(User bidder, double newMaxBid) {
        if (status.equals(STATUS_DELETED)) {
            System.out.println("[Error]: " + RED + "The auction session has been deleted by Admin" + RESET);
            return null;
        }

        if (!status.equals(STATUS_RUNNING) || LocalDateTime.now().isAfter(endTime)) {
            System.out.println("[Error]: " + RED + "Cannot place a bid. The auction is not running or has already ended" + RESET);
            return null;
        }

        if (newMaxBid < 0) {
            System.out.println("[Error]: " + RED + "Invalid Bid" + RESET);
            return null;
        }

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

        // Anti-Sniping Algorithm with Hard-Cap Limit
        if (LocalDateTime.now().plusMinutes(1).isAfter(endTime)) {
            LocalDateTime proposedEndTime = endTime.plusMinutes(2);
            // Ensure the new end time NEVER exceeds the hard-cap maxEndTime
            if (proposedEndTime.isAfter(maxEndTime)) {
                endTime = maxEndTime;
                System.out.println(YELLOW + "[System]: Anti-sniping triggered but hit hard-cap limit. End time: " + endTime + RESET);
            } else {
                endTime = proposedEndTime;
                System.out.println(YELLOW + "[System]: Time increased 2 minutes (Anti-sniping triggered). End time: " + endTime + RESET);
            }
        }

        return transaction;
    }

    /**
     * Reverts a specific failed bid transaction. This is typically used to roll back the in-memory
     * state if the corresponding database transaction fails.
     *
     * @param previousWinner        The user who was winning before the failed bid.
     * @param previousHighestMaxBid The highest max bid before the failed bid.
     * @param failedTransaction     The specific bid transaction that failed and needs to be removed.
     */
    public synchronized void revertLastBid(User previousWinner, double previousHighestMaxBid, BidTransaction failedTransaction) {
        // 1. Remove the specific failed transaction from bidHistory
        if (failedTransaction != null) {
            bidHistory.remove(failedTransaction);
        }

        // 2. Restore winning bidder and highest max bid to the state before the failed transaction
        this.winningBidder = previousWinner;
        this.highestMaxBid = previousHighestMaxBid;

        // 3. Recalculate currentPrice based on the remaining bid history
        if (bidHistory.isEmpty()) {
            this.currentPrice = item.getStartingPrice();
        } else {
            // The currentPrice should be the bidAmount of the last valid transaction
            this.currentPrice = bidHistory.get(bidHistory.size() - 1).getBidAmount();
        }
        System.out.println(YELLOW + "[System]: RAM State Reverted to Previous Winner: " + (previousWinner != null ? previousWinner.getUserName() : "None") + RESET);
    }

    /**
     * Evaluates the current system time against the auction's end time.
     * Transitions the status to FINISHED if there is a winner, or CANCELED if no bids were placed.
     */
    public synchronized void closeAuctionIfTimeIsUp() {
        if ((this.status.equals(STATUS_RUNNING) || this.status.equals(STATUS_OPEN)) && LocalDateTime.now().isAfter(this.endTime)) {
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
     * Registers a new automated bidding bot (AutoBid) for a user on this auction.
     * The bot is placed into a PriorityQueue for chronological processing.
     *
     * @param bidder        The user configuring the auto-bid.
     * @param maxBid        The absolute maximum amount the user is willing to spend.
     * @param userIncrement The incremental step amount to increase the price when outbidding.
     * @return {@code true} if the registration is successful; {@code false} if constraints fail.
     */
    public synchronized boolean registerAutoBid(User bidder, double maxBid, double userIncrement) {
        if (!status.equals(STATUS_RUNNING)) {
            System.out.println("[Error]: " + RED + "Auction is not in RUNNING status" + RESET);
            return false;
        }

        if (maxBid <= currentPrice) {
            System.out.println("[Error]: " + RED + "Maximum bid must be greater than current price" + RESET);
            return false;
        }

        AutoBid newAutoBid = new AutoBid(bidder, maxBid, userIncrement);
        activeAutoBids.offer(newAutoBid);

        System.out.println(BLUE + "[Auto-Bid]: \"" + bidder.getUserName() + "\" registered Auto-Bid successfully (Max: " + maxBid + ")" + RESET);

        return true;
    }

    /**
     * Returns a summary string containing the core details of this auction session.
     *
     * @return Formatted information string.
     */
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