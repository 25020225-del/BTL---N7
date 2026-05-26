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
 * Domain model aggregate root representing the state machine of an active auction room session.
 * Coordinates real-time price mutations, executes multi-agent anti-sniping chronographic extensions,
 * and maintains prioritized processing queues for proxy-bidding subsystems.
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
     * Fully hydrates a structural auction aggregate state machine configuration mapping.
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
     * Factory assembly routine initializing a deferred-clock auction session pending activation triggers.
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

    /**
     * Hydrates a concrete structural instance mapping directly from a database payload frame.
     */
    public static Auction buildAuctionFromMap(Map<String, Object> map) {
        Auction auction = new Auction();
        auction.setId((String) map.get("id"));

        Object startingPriceVal = map.get("startingPrice");
        long startingPrice = startingPriceVal instanceof Number ? ((Number) startingPriceVal).longValue() : 0L;

        Item item = ItemFactory.createItem(
                (String) map.get("itemType"),
                "ITM-" + map.get("id"),
                (String) map.get("itemName"),
                (String) map.get("description"),
                startingPrice
        );
        item.setImageUrl((String) map.get("imageUrl"));
        auction.setItem(item);

        User seller = new User();
        seller.setId((String) map.get("sellerId"));
        auction.setSeller(seller);

        Object currentPriceVal = map.get("currentPrice");
        long currentPrice = currentPriceVal instanceof Number ? ((Number) currentPriceVal).longValue() : 0L;
        auction.setCurrentPrice(currentPrice);

        Object endTimeVal = map.get("endTime");
        if (endTimeVal instanceof Number endTimeNum) {
            auction.setEndTime(
                    Instant.ofEpochMilli(endTimeNum.longValue())
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()
            );
        } else {
            auction.setEndTime(null);
        }

        String winnerId = (String) map.get("winningBidderId");
        if (winnerId != null) {
            User winner = new User();
            winner.setId(winnerId);
            winner.setUserName((String) map.get("winnerName"));
            auction.setWinningBidder(winner);
        }
        return auction;
    }

    /**
     * Value capsule encapsulating the structural parameters computed from a provisional pricing challenge.
     */
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
     * Evaluates an inbound transaction price challenge against invariant rules without mutating internal states.
     *
     * @param bidder    the actor profile context dispatching the price challenge
     * @param newMaxBid the maximum monetary absolute overhead cap ceiling offered
     * @return calculated immutable {@link BidResult} snapshot metrics, or null if boundaries violate domain constraints
     */
    public BidResult calculateBidResult(User bidder, long newMaxBid) {
        return calculateBidResult(bidder, newMaxBid, false);
    }

    public BidResult calculateBidResult(User bidder, long newMaxBid, boolean isManual) {
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

        if (isManual) {
            if (winningBidder == null) {
                nextCurrentPrice = newMaxBid;
                nextHighestMaxBid = newMaxBid;
                nextWinner = bidder;
            } else if (bidder.getId().equals(winningBidder.getId())) {
                if (newMaxBid > highestMaxBid) {
                    nextHighestMaxBid = newMaxBid;
                    nextCurrentPrice = newMaxBid;
                }
            } else {
                if (newMaxBid > highestMaxBid) {
                    nextCurrentPrice = newMaxBid;
                    nextHighestMaxBid = newMaxBid;
                    nextWinner = bidder;
                } else {
                    nextCurrentPrice = newMaxBid + bidIncrement;
                    if (nextCurrentPrice > highestMaxBid) {
                        nextCurrentPrice = highestMaxBid;
                    }
                }
            }
        } else {
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
     * Commits a pre-verified evaluation state token into internal aggregate attributes boundaries.
     *
     * @param bidder the user entity binding the finalized price modification
     * @param result the validated structural transaction values statements container
     * @return newly appended historical ledger transaction instance record
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
     * Evaluates clock indicators to transition active processing states into terminal finished fields safely.
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
     * Rolls back and invalidates state side-effects caused by a failed downstream database ledger commit sequence.
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
     * Registers and enqueues an automated proxy bot configuration rule to the priority pipeline.
     * Enforces strict minimum liquidity alignment constraints to prevent orphaned lock states.
     *
     * @param bidder        the actor initializing automated system agency properties
     * @param maxBid        the explicit spending upper bound threshold mandated by the caller
     * @param userIncrement the per-step reactive modification margin added to counter-bids
     * @return true if configuration parameters satisfy activation boundaries, false otherwise
     */
    public boolean registerAutoBid(User bidder, long maxBid, long userIncrement) {
        boolean isActive = STATUS_RUNNING.equals(status) || STATUS_WAITING_FOR_BID.equals(status);
        if (!isActive) {
            return false;
        }

        if (userIncrement < bidIncrement) {
            return false;
        }

        long minRequired = (winningBidder == null) ? currentPrice : (currentPrice + bidIncrement);
        if (maxBid < minRequired) {
            return false;
        }

        activeAutoBids.offer(new AutoBid(bidder, maxBid, userIncrement));
        return true;
    }

    /**
     * Computes the dynamic absolute lower-bound threshold matrix required to activate proxy engine handlers.
     */
    public long getMinAutoBidRequired() {
        return (winningBidder == null) ? currentPrice : (currentPrice + bidIncrement);
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