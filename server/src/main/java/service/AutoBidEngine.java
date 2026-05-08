package service;

import controller.ServerBidderController;
import model.auction.Auction;
import model.auction.AutoBid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The core engine responsible for managing and executing automated bidding logic.
 * It processes registered bots asynchronously using a priority-based approach
 * (First-In, First-Out by registration time) to ensure fairness and prevent thread starvation.
 * <p>
 * Part of the Auction System project.
 */
public class AutoBidEngine {
    private static final Logger log = LoggerFactory.getLogger(AutoBidEngine.class);

    /**
     * Fixed thread pool for managing concurrent bot execution.
     */
    private static final int MAX_BOTPOOL_SIZE = 50;
    private static final ExecutorService botPool = Executors.newFixedThreadPool(MAX_BOTPOOL_SIZE);

    /**
     * Controller used to handle the actual bid placement and wallet transactions.
     * This field is now non-final to allow for Dependency Injection from the server setup.
     */
    private static ServerBidderController bidderCtrl;

    /**
     * Injects the required controller into the engine.
     *
     * @param ctrl The bidder controller instance.
     */
    public static void setBidderController(ServerBidderController ctrl) {
        bidderCtrl = ctrl;
    }

    /**
     * Entry point to trigger an automated scan for a specific auction.
     * This is usually called after a manual bid or a new bot registration.
     *
     * @param auction The auction session to be scanned for potential bot actions.
     */
    public static void triggerBotScan(Auction auction) {
        botPool.submit(() -> processNextBot(auction));
    }

    /**
     * Mathematically evaluates the bot queue in RAM to determine the final winner
     * and submits exactly ONE database transaction to prevent queue congestion.
     *
     * @param auction The active auction session being processed.
     */
    private static void processNextBot(Auction auction) {
        if (!auction.getStatus().equals(Auction.STATUS_RUNNING)) {
            return;
        }

        // Lấy tham chiếu của hàng đợi bots hiện tại
        java.util.Queue<AutoBid> botQueue = auction.getActiveAutoBids();
        List<AutoBid> bots;

        // 1. KHÓA HÀNG ĐỢI KHI ĐỌC/COPY ĐỂ TRÁNH ConcurrentModificationException
        synchronized (botQueue) {
            if (botQueue.isEmpty()) return;
            bots = new ArrayList<>(botQueue);
        }

        // 2. Sort bots: Highest maxBid first. If maxBid is tied, earlier registration time wins.
        bots.sort((b1, b2) -> {
            int maxBidCompare = Long.compare(b2.getMaxBid(), b1.getMaxBid());
            if (maxBidCompare != 0) return maxBidCompare;
            return b1.getTimeRegistered().compareTo(b2.getTimeRegistered());
        });

        AutoBid top1 = null;
        AutoBid top2 = null;

        // 3. Find the top 2 bots capable of bidding
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
                    break; // We only need the top 2 bots for the math fight
                }
            }
        }

        if (top1 == null) return; // No capable bot found

        // 4. Calculate the final winning price mathematically
        long finalPrice;
        long top1ActualIncrement = Math.max(auction.getBidIncrement(), top1.getIncrement());

        if (top2 != null) {
            // Price = maxBid of bot 2 + increment of bot 1 (capped at maxBid of bot 1)
            finalPrice = Math.min(top1.getMaxBid(), top2.getMaxBid() + top1ActualIncrement);

            final long top2MaxBid = top2.getMaxBid();
            final String top1Id = top1.getBidder().getId();

            // 5A. KHÓA HÀNG ĐỢI KHI XÓA PHẦN TỬ THUA CUỘC
            synchronized (botQueue) {
                botQueue.removeIf(b ->
                        b.getMaxBid() <= top2MaxBid && !b.getBidder().getId().equals(top1Id)
                );
            }
        } else {
            // Only top1 is capable of bidding
            finalPrice = (auction.getWinningBidder() == null) ?
                    auction.getItem().getStartingPrice() :
                    auction.getCurrentPrice() + top1ActualIncrement;
        }

        // Ensure final price is at least the minimum required to take the lead
        long minRequired = (auction.getWinningBidder() == null) ?
                auction.getItem().getStartingPrice() :
                auction.getCurrentPrice() + top1ActualIncrement;
        finalPrice = Math.max(finalPrice, minRequired);

        // Check if top1 is already winning and the price hasn't been pushed up
        if (auction.getWinningBidder() != null && auction.getWinningBidder().getId().equals(top1.getBidder().getId())) {
            if (finalPrice <= auction.getCurrentPrice()) {
                return; // Already winning at a sufficient price
            }
        }

        final AutoBid winnerBot = top1;
        log.info("Bot of {} mathematically won. Submitting transaction.", winnerBot.getBidder().getUserName());

        // 6. Submit exactly ONE task to the Database
        bidderCtrl.placeBidOnAuction(winnerBot.getBidder(), auction, finalPrice, true)
                .thenAccept(success -> {
                    if (!success) {
                        log.info("Bot of {} failed (insufficient balance). Removing configuration.", winnerBot.getBidder().getUserName());

                        // 5B. KHÓA HÀNG ĐỢI KHI XÓA BOT BỊ LỖI THANH TOÁN
                        synchronized (botQueue) {
                            botQueue.removeIf(b ->
                                    b.getBidder().getId().equals(winnerBot.getBidder().getId())
                            );
                        }

                        // Re-trigger scan to allow the next best bot to take over
                        processNextBot(auction);
                    }
                }).exceptionally(ex -> {
                    log.error("Bot Engine execution failed: {}", ex.getMessage());
                    return null;
                });
    }
}