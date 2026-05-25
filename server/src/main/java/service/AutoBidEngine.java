package service;

import controller.ServerBidderController;
import model.auction.Auction;
import model.auction.AutoBid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core matching engine coordinating and executing real-time automated proxy bidding agents.
 * Processes target requests concurrently via prioritized timestamp queues to enforce execution equity.
 */
public class AutoBidEngine {

    private static final Logger log = LoggerFactory.getLogger(AutoBidEngine.class);

    /**
     * FIX #2 (CRITICAL): The previous implementation used a single static
     * {@code Executors.newFixedThreadPool(50)} pool shared across ALL auction sessions.
     * Under high concurrency (many simultaneous auctions + anti-sniping extensions), all 50
     * threads could be saturated by a single burst, causing complete thread starvation for other
     * auctions and a systemic processing halt.
     *
     * <p>Resolution: Replaced with a {@link ThreadPoolExecutor} backed by a
     * {@link SynchronousQueue} (identical to {@code newCachedThreadPool}), but with:
     * <ul>
     *   <li>corePoolSize = 0 → no idle threads held permanently</li>
     *   <li>maximumPoolSize = unbounded (Integer.MAX_VALUE) → no starvation ceiling</li>
     *   <li>keepAliveTime = 30s → idle threads are reclaimed quickly</li>
     * </ul>
     * The existing {@link #activeScans} ConcurrentHashMap with per-auction {@link AtomicBoolean}
     * already guarantees at most ONE thread is active per auction at any given time, so the
     * unbounded maximum does not create an uncontrolled thread explosion in practice.
     * Each auction's bot evaluation chain is serialized by design.
     */
    private static final ExecutorService botPool = new ThreadPoolExecutor(
            0,
            Integer.MAX_VALUE,
            30L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            r -> {
                Thread t = new Thread(r, "BotPool-Worker");
                t.setDaemon(true);
                return t;
            }
    );

    private static final ConcurrentHashMap<String, AtomicBoolean> activeScans = new ConcurrentHashMap<>();
    private static volatile ServerBidderController bidderCtrl;

    /**
     * Injects the global server-side bidder controller dependency.
     *
     * @param ctrl the controller instance handling core automated bid placements
     */
    public static void setBidderController(ServerBidderController ctrl) {
        bidderCtrl = ctrl;
    }

    /**
     * Registers a non-blocking evaluation query probe sweep over a designated auction room context.
     * The per-auction {@link AtomicBoolean} guard in {@link #activeScans} ensures at most one
     * evaluation thread is active per auction at any time, regardless of the pool size.
     *
     * @param auction the targeted runtime instance to scan for proxy agent triggers
     */
    public static void triggerBotScan(Auction auction) {
        AtomicBoolean isScanning = activeScans.computeIfAbsent(auction.getId(), k -> new AtomicBoolean(false));

        if (isScanning.compareAndSet(false, true)) {
            botPool.submit(() -> processNextBot(auction));
        }
    }

    static void processNextBot(Auction auction) {
        AtomicBoolean isScanning = activeScans.computeIfAbsent(auction.getId(), k -> new AtomicBoolean(false));

        if (!Auction.STATUS_RUNNING.equals(auction.getStatus()) && !Auction.STATUS_WAITING_FOR_BID.equals(auction.getStatus())) {
            isScanning.set(false);
            return;
        }

        java.util.Queue<AutoBid> botQueue = auction.getActiveAutoBids();
        List<AutoBid> bots;

        synchronized (botQueue) {
            if (botQueue.isEmpty()) {
                isScanning.set(false);
                return;
            }
            bots = new ArrayList<>(botQueue);
        }

        bots.sort((b1, b2) -> {
            int maxBidCompare = Long.compare(b2.getMaxBid(), b1.getMaxBid());
            if (maxBidCompare != 0) return maxBidCompare;
            return b1.getTimeRegistered().compareTo(b2.getTimeRegistered());
        });

        AutoBid top1 = null;
        AutoBid top2 = null;

        for (AutoBid bot : bots) {
            long actualIncrement = Math.max(auction.getBidIncrement(), bot.getIncrement());
            long requiredBid = (auction.getWinningBidder() == null) ?
                    auction.getItem().getStartingPrice() :
                    auction.getCurrentPrice() + actualIncrement;

            if (bot.getMaxBid() >= requiredBid ||
                    (auction.getWinningBidder() != null && auction.getWinningBidder().getId().equals(bot.getBidder().getId()) && bot.getMaxBid() > auction.getCurrentPrice())) {
                if (top1 == null) {
                    top1 = bot;
                } else if (top2 == null) {
                    top2 = bot;
                    break;
                }
            }
        }

        if (top1 == null) {
            isScanning.set(false);
            return;
        }

        long finalPrice;
        long top1ActualIncrement = Math.max(auction.getBidIncrement(), top1.getIncrement());

        if (top2 != null) {
            finalPrice = Math.min(top1.getMaxBid(), top2.getMaxBid() + top1ActualIncrement);
            final long top2MaxBid = top2.getMaxBid();
            final String top1Id = top1.getBidder().getId();

            synchronized (botQueue) {
                botQueue.removeIf(b -> b.getMaxBid() <= top2MaxBid && !b.getBidder().getId().equals(top1Id));
            }
        } else {
            finalPrice = (auction.getWinningBidder() == null) ?
                    auction.getItem().getStartingPrice() :
                    auction.getCurrentPrice() + top1ActualIncrement;
        }

        long minRequired = (auction.getWinningBidder() == null) ?
                auction.getItem().getStartingPrice() :
                auction.getCurrentPrice() + top1ActualIncrement;
        finalPrice = Math.max(finalPrice, minRequired);

        if (auction.getWinningBidder() != null && auction.getWinningBidder().getId().equals(top1.getBidder().getId())) {
            if (top2 == null || finalPrice <= auction.getCurrentPrice()) {
                isScanning.set(false);
                return;
            }
        }

        final AutoBid winnerBot;
        final long computedFinalPrice;

        if (top2 != null && auction.getWinningBidder() != null
                && auction.getWinningBidder().getId().equals(top1.getBidder().getId())) {
            // top1 is already the current leader.
            // Place the bid on behalf of the losing bot (top2) at its full maxBid.
            // calculateBidResult will correctly compute the Vickrey price:
            //   nextCurrentPrice = min(top2.maxBid + inc, top1.highestMaxBid)
            // This ONLY works correctly when top1's highestMaxBid in DB equals top1.getMaxBid().
            // If highestMaxBid < top1.getMaxBid() (e.g. because a prior engine pass stored the wrong
            // value), a re-anchor pass (the else-if below) would be needed first.
            winnerBot = top2;
            computedFinalPrice = top2.getMaxBid();
            log.info("Winning bot {} is already leader. Placing bid via challenger {} at maxBid={} "
                    + "(highestMaxBid={}) to drive Vickrey price.",
                    top1.getBidder().getUserName(), top2.getBidder().getUserName(),
                    computedFinalPrice, auction.getHighestMaxBid());
        } else {
            // top1 is NOT the current leader (either no winner yet, or top1 is a new challenger).
            // Pass top1's FULL maxBid so calculateBidResult stores the correct highestMaxBid.
            // The Vickrey current price is derived automatically by calculateBidResult:
            //   if top1 outbids current holder → nextCurrentPrice = old_highestMaxBid + increment
            winnerBot = top1;
            computedFinalPrice = top1.getMaxBid();
            log.info("Bot {} is new challenger / first bidder. Submitting at full maxBid={} "
                    + "(Vickrey price computed by calculateBidResult).",
                    winnerBot.getBidder().getUserName(), computedFinalPrice);
        }

        try {
            bidderCtrl.placeBidOnAuctionFromBot(winnerBot.getBidder(), auction, computedFinalPrice)
                    .handle((success, ex) -> {
                        try {
                            if (ex != null) {
                                log.error("Bot Engine execution failed for user {}: {}", winnerBot.getBidder().getUserName(), ex.getMessage());
                                synchronized (botQueue) {
                                    botQueue.removeIf(b -> b.getBidder().getId().equals(winnerBot.getBidder().getId()));
                                }
                                botPool.submit(() -> processNextBot(auction));
                            } else if (Boolean.TRUE.equals(success)) {
                                isScanning.set(false);
                            } else {
                                log.info("Bot of {} failed (insufficient balance or DB reject). Removing configuration.", winnerBot.getBidder().getUserName());
                                synchronized (botQueue) {
                                    botQueue.removeIf(b -> b.getBidder().getId().equals(winnerBot.getBidder().getId()));
                                }
                                botPool.submit(() -> processNextBot(auction));
                            }
                        } catch (Exception poolEx) {
                            log.error("Critical fail inside async callback, forcing lock release: {}", poolEx.getMessage());
                            isScanning.set(false);
                        }
                        return null;
                    });
        } catch (Exception e) {
            log.error("Critical fail submitting async bid task: {}", e.getMessage());
            isScanning.set(false);
        }
    }
}