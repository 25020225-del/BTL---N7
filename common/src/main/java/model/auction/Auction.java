package model.auction;

import model.base.Entity;
import model.finance.BidTransaction;
import model.item.Item;
import model.item.ItemFactory;
import model.user.User;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Domain model aggregate representing an active auction room session lifecycle.
 * Coordinates real-time price updates, infinite anti-sniping increments, and proxy bidding queues.
 */
public class Auction extends Entity {

    public static final String STATUS_PENDING = "PENDING_APPROVAL";
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_WAITING_FOR_BID = "WAITING_FOR_BID";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_FINISHED = "FINISHED";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_DELETED = "DELETED";

    public static final long ANTI_SNIPING_THRESHOLD_SECONDS = 60;
    public static final long ANTI_SNIPING_EXTENSION_SECONDS = 120;

    private Item item;
    private User seller;
    private long currentPrice;
    private long highestMaxBid;
    private long bidIncrement;
    private User winningBidder;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int durationMinutes;
    private List<BidTransaction> bidHistory = new ArrayList<>();
    private PriorityBlockingQueue<AutoBid> activeAutoBids;

    public Auction() {
        super();
        this.activeAutoBids = new PriorityBlockingQueue<>(
                11, Comparator.comparing(AutoBid::getTimeRegistered));
    }

    /**
     * Fully hydrates an auction cluster configuration mapping initialization state fields.
     */
    public Auction(String id, Item item, User seller,
                   long bidIncrement,
                   LocalDateTime startTime, LocalDateTime endTime,
                   int durationMinutes) {
        super(id);
        this.item = item;
        this.seller = seller;
        this.currentPrice = item.getStartingPrice();
        this.highestMaxBid = 0;
        this.bidIncrement = bidIncrement;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMinutes = durationMinutes;
        this.bidHistory = new ArrayList<>();
        this.activeAutoBids = new PriorityBlockingQueue<>(
                11, Comparator.comparing(AutoBid::getTimeRegistered));
        this.status = seller.isGood() ? STATUS_OPEN : STATUS_PENDING;
    }

    /**
     * @deprecated Use 7-parameter constructor to explicitly supply structural original duration bounds.
     */
    @Deprecated
    public Auction(String id, Item item, User seller,
                   long bidIncrement,
                   LocalDateTime startTime, LocalDateTime endTime) {
        this(id, item, seller, bidIncrement, startTime, endTime,
                (endTime != null && startTime != null)
                        ? (int) java.time.Duration.between(startTime, endTime).toMinutes()
                        : 60);
    }

    /**
     * Factory method initializing a deferred-clock auction session pending initial activation triggers.
     *
     * @return initialized Auction entity wrapped in a pending or open validation lifecycle state
     */
    public static Auction createNewAuction(Item item, User seller,
                                           long bidIncrement,
                                           LocalDateTime startTime,
                                           int durationMinutes) {
        String newId = "AUC-" + utils.IdGenerator.generateUUIDv7();
        Auction newAuction = new Auction(newId, item, seller, bidIncrement, startTime, null, durationMinutes);
        newAuction.setStatus(seller.isGood() ? STATUS_OPEN : STATUS_PENDING);
        return newAuction;
    }

    public static Auction buildAuctionFromMap(Map<String, Object> map) {
        Auction auction = new Auction();
        auction.setId((String) map.get("id"));

        Item item = ItemFactory.createItem(
                (String) map.get("itemType"),
                "ITM-" + map.get("id"),
                (String) map.get("itemName"),
                (String) map.get("description"),
                ((Number) map.get("startingPrice")).longValue()
        );
        item.setImageUrl((String) map.get("imageUrl"));
        auction.setItem(item);

        User seller = new User();
        seller.setId((String) map.get("sellerId"));
        auction.setSeller(seller);
        auction.setCurrentPrice(((Number) map.get("currentPrice")).longValue());
        auction.setEndTime(
                Instant.ofEpochMilli(((Number) map.get("endTime")).longValue())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
        );
        return auction;
    }

    public static class BidResult {
        public final User newWinner;
        public final long newHighestMaxBid;
        public final long newCurrentPrice;
        public final LocalDateTime newEndTime;
        public final boolean isFirstBid;

        public BidResult(User newWinner, long newHighestMaxBid,
                         long newCurrentPrice, LocalDateTime newEndTime,
                         boolean isFirstBid) {
            this.newWinner = newWinner;
            this.newHighestMaxBid = newHighestMaxBid;
            this.newCurrentPrice = newCurrentPrice;
            this.newEndTime = newEndTime;
            this.isFirstBid = isFirstBid;
        }
    }

    /**
     * Evaluates an incoming pricing challenge parameter context without mutating current in-memory field parameters.
     *
     * @param bidder    the user profile initiating the evaluation assertion
     * @param newMaxBid top monetary threshold bound offered down the channel
     * @return calculated immutable {@link BidResult} snapshot parameters, or null if requirements break constraints
     */
    public BidResult calculateBidResult(User bidder, long newMaxBid) {
        boolean isWaiting = STATUS_WAITING_FOR_BID.equals(status);
        boolean isRunning = STATUS_RUNNING.equals(status);

        if (!isWaiting && !isRunning) {
            return null;
        }

        if (isRunning) {
            if (endTime == null || LocalDateTime.now().isAfter(endTime)) {
                return null;
            }
        }

        long minRequiredBid = (winningBidder == null) ? currentPrice : (currentPrice + bidIncrement);
        if (newMaxBid < minRequiredBid) {
            return null;
        }

        User nextWinner = winningBidder;
        long nextHighestMaxBid = highestMaxBid;
        long nextCurrentPrice = currentPrice;

        if (winningBidder == null) {
            nextCurrentPrice = item.getStartingPrice();
            nextHighestMaxBid = newMaxBid;
            nextWinner = bidder;

        } else if (bidder.getId().equals(winningBidder.getId())) {
            if (newMaxBid > highestMaxBid) {
                nextHighestMaxBid = newMaxBid;
            }

        } else {
            if (newMaxBid > highestMaxBid) {
                nextCurrentPrice = highestMaxBid + bidIncrement;
                if (nextCurrentPrice > newMaxBid) {
                    nextCurrentPrice = newMaxBid;
                }
                nextHighestMaxBid = newMaxBid;
                nextWinner = bidder;
            } else {
                nextCurrentPrice = newMaxBid + bidIncrement;
                if (nextCurrentPrice > highestMaxBid) {
                    nextCurrentPrice = highestMaxBid;
                }
            }
        }

        LocalDateTime nextEndTime;
        if (isWaiting) {
            nextEndTime = LocalDateTime.now().plusMinutes(durationMinutes);
            return new BidResult(nextWinner, nextHighestMaxBid, nextCurrentPrice, nextEndTime, true);
        }

        nextEndTime = endTime;
        LocalDateTime now = LocalDateTime.now();
        long secondsRemaining = Duration.between(now, endTime).getSeconds();

        if (secondsRemaining > 0 && secondsRemaining <= ANTI_SNIPING_THRESHOLD_SECONDS) {
            nextEndTime = endTime.plusSeconds(ANTI_SNIPING_EXTENSION_SECONDS);
        }

        return new BidResult(nextWinner, nextHighestMaxBid, nextCurrentPrice, nextEndTime, false);
    }

    /**
     * Commits a pre-calculated evaluation record into internal state boundaries.
     * Transitions life-cycle flags automatically if transaction scopes represent structural triggers.
     *
     * @param bidder the authenticated entity launching the validated bid
     * @param result pre-calculated structural value parameters statement
     * @return newly generated persistent transaction record
     */
    public BidTransaction applyBidResult(User bidder, BidResult result) {
        this.winningBidder = result.newWinner;
        this.highestMaxBid = result.newHighestMaxBid;
        this.currentPrice = result.newCurrentPrice;
        this.endTime = result.newEndTime;

        if (result.isFirstBid) {
            this.status = STATUS_RUNNING;
        }

        BidTransaction transaction = new BidTransaction(
                "TXN-" + System.currentTimeMillis(), bidder, currentPrice);
        bidHistory.add(transaction);
        return transaction;
    }

    /**
     * Evaluates clock boundaries to safely transit running states into terminal finished structures.
     */
    public void closeAuctionIfTimeIsUp() {
        if (endTime == null) {
            return;
        }
        if (STATUS_RUNNING.equals(this.status) && LocalDateTime.now().isAfter(this.endTime)) {
            this.status = STATUS_FINISHED;
        }
    }

    /**
     * Erases and reverts the state effects of a failed in-memory transaction block execution context.
     */
    public void revertLastBid(User previousWinner, long previousHighestMaxBid,
                              LocalDateTime previousEndTime, String previousStatus,
                              BidTransaction failedTransaction) {
        if (failedTransaction != null) {
            bidHistory.remove(failedTransaction);
        }
        this.winningBidder = previousWinner;
        this.highestMaxBid = previousHighestMaxBid;
        this.endTime = previousEndTime;
        this.status = previousStatus;

        if (bidHistory.isEmpty()) {
            this.currentPrice = item.getStartingPrice();
        } else {
            this.currentPrice = bidHistory.get(bidHistory.size() - 1).getBidAmount();
        }
    }

    /**
     * Enqueues an automated proxy bot agent configuration parameter structure into the prioritizing queue.
     */
    public boolean registerAutoBid(User bidder, long maxBid, long userIncrement) {
        boolean isActive = STATUS_RUNNING.equals(status) || STATUS_WAITING_FOR_BID.equals(status);
        if (!isActive) {
            return false;
        }
        if (maxBid <= currentPrice) {
            return false;
        }
        activeAutoBids.offer(new AutoBid(bidder, maxBid, userIncrement));
        return true;
    }

    /**
     * @deprecated Use atomic decoupled pipelines via {@link #calculateBidResult} and {@link #applyBidResult}
     */
    @Deprecated
    public BidTransaction placeBid(User bidder, long newMaxBid) {
        boolean canBid = STATUS_WAITING_FOR_BID.equals(status) || STATUS_RUNNING.equals(status);
        if (!canBid) return null;
        if (STATUS_RUNNING.equals(status) && (endTime == null || LocalDateTime.now().isAfter(endTime))) {
            return null;
        }
        if (newMaxBid < 0) return null;

        long minRequired = (winningBidder == null) ? currentPrice : (currentPrice + bidIncrement);
        if (newMaxBid < minRequired) return null;

        BidResult result = calculateBidResult(bidder, newMaxBid);
        if (result == null) return null;
        return applyBidResult(bidder, result);
    }

    @Override
    public String getInfo() {
        return "=== AUCTION INFORMATION ===\n"
                + "Auction ID   : " + this.getId() + "\n"
                + "Item         : " + (item != null ? item.getItemName() : "N/A") + "\n"
                + "Current Price: VND " + this.currentPrice + "\n"
                + "Leading Bidder: " + (winningBidder != null ? winningBidder.getUserName() : "None") + "\n"
                + "Status       : " + this.status + "\n"
                + "End Time     : " + (endTime != null ? endTime : "Chờ bid đầu tiên…") + "\n"
                + "Duration     : " + this.durationMinutes + " phút";
    }

    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }
    public User getSeller() { return seller; }
    public void setSeller(User seller) { this.seller = seller; }
    public long getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(long currentPrice) { this.currentPrice = currentPrice; }
    public long getHighestMaxBid() { return highestMaxBid; }
    public void setHighestMaxBid(long highestMaxBid) { this.highestMaxBid = highestMaxBid; }
    public long getBidIncrement() { return bidIncrement; }
    public void setBidIncrement(long bidIncrement) { this.bidIncrement = bidIncrement; }
    public User getWinningBidder() { return winningBidder; }
    public void setWinningBidder(User winningBidder) { this.winningBidder = winningBidder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public List<BidTransaction> getBidHistory() { return bidHistory; }
    public void setBidHistory(List<BidTransaction> bidHistory) { this.bidHistory = bidHistory; }
    public PriorityBlockingQueue<AutoBid> getActiveAutoBids() { return activeAutoBids; }
}