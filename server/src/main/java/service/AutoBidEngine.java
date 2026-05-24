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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core matching service coordinating and executing real-time automated proxy bidding agents.
 * Processes target requests concurrently via prioritized timestamp queues to enforce strict
 * execution equity.
 */
public class AutoBidEngine {

    private static final Logger log = LoggerFactory.getLogger(AutoBidEngine.class);
    private static final int MAX_BOTPOOL_SIZE = 50;
    private static final ExecutorService botPool = Executors.newFixedThreadPool(MAX_BOTPOOL_SIZE);
    private static final ConcurrentHashMap<String, AtomicBoolean> activeScans = new ConcurrentHashMap<>();
    private static ServerBidderController bidderCtrl;

    public static void setBidderController(ServerBidderController ctrl) {
        bidderCtrl = ctrl;
    }

    /**
     * Registers a non-blocking evaluate query probe sweep over a designated auction room context.
     *
     * @param auction targeted runtime instance to scan for proxy agent triggers
     */
    public static void triggerBotScan(Auction auction) {
        AtomicBoolean isScanning = activeScans.computeIfAbsent(auction.getId(), k -> new AtomicBoolean(false));

        if (isScanning.compareAndSet(false, true)) {
            botPool.submit(() -> processNextBot(auction));
        }
    }

    static void processNextBot(Auction auction) {
        AtomicBoolean isScanning = activeScans.computeIfAbsent(auction.getId(), k -> new AtomicBoolean(false));

        if (!auction.getStatus().equals(Auction.STATUS_RUNNING)) {
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
            if (finalPrice <= auction.getCurrentPrice()) {
                isScanning.set(false);
                return;
            }
        }

        final AutoBid winnerBot = top1;
        // [BUG-2 FIX] Trước đây truyền winnerBot.getMaxBid() vào placeBidOnAuctionFromBot,
        // dẫn đến dual-calculation: Engine tính finalPrice theo Vickrey logic (dùng
        // top1ActualIncrement = max(auctionIncrement, bot.increment)), nhưng DB lại tính
        // lại price độc lập từ newMaxBid=maxBid dùng auctionIncrement chuẩn. Khi
        // top1.getIncrement() > auction.getBidIncrement(), hai kết quả lệch nhau → audit
        // trail sai, DB là nguồn truth không đồng bộ với Engine.
        //
        // Fix: truyền chính xác finalPrice đã được Engine tính (Vickrey proxy price) vào
        // controller. DB nhận finalPrice làm newMaxBid → commit đúng mức giá, không
        // tính lại độc lập → eliminates dual-calculation hoàn toàn.
        final long computedFinalPrice = finalPrice;
        log.info("Bot of {} mathematically won at Vickrey price={}. Submitting ASYNC transaction.",
                winnerBot.getBidder().getUserName(), computedFinalPrice);

        try {
            // [BUG-2 FIX] Truyền computedFinalPrice thay vì winnerBot.getMaxBid().
            // Engine là nguồn tính giá, DB là nguồn commit — nhất quán, không dual-calc.
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