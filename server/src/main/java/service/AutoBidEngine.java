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

    private static final ConcurrentHashMap<String, AtomicBoolean> activeScans = new ConcurrentHashMap<>();

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
        AtomicBoolean isScanning = activeScans.computeIfAbsent(auction.getId(), k -> new AtomicBoolean(false));

        // Cơ chế Non-blocking Lock: Nếu đã có thread quét phiên này rồi, các thread khác sẽ bị từ chối
        if (isScanning.compareAndSet(false, true)) {
            // Đẩy task đánh giá vào Pool. Việc nhả Lock (isScanning.set(false))
            // sẽ do processNextBot tự chịu trách nhiệm khi luồng bất đồng bộ thực sự hoàn tất.
            botPool.submit(() -> processNextBot(auction));
        }
    }

    /**
     * Mathematically evaluates the bot queue in RAM to determine the final winner
     * and submits exactly ONE database transaction to prevent queue congestion.
     * Fully Asynchronous to prevent Thread Starvation.
     *
     * @param auction The active auction session being processed.
     */
    static void processNextBot(Auction auction) {
        AtomicBoolean isScanning = activeScans.computeIfAbsent(auction.getId(), k -> new AtomicBoolean(false));

        if (!auction.getStatus().equals(Auction.STATUS_RUNNING)) {
            isScanning.set(false); // Release Lock
            return;
        }

        java.util.Queue<AutoBid> botQueue = auction.getActiveAutoBids();
        List<AutoBid> bots;

        // 1. KHÓA HÀNG ĐỢI KHI ĐỌC/COPY ĐỂ TRÁNH ConcurrentModificationException
        synchronized (botQueue) {
            if (botQueue.isEmpty()) {
                isScanning.set(false); // Release Lock
                return;
            }
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

        // Thoát nếu không còn bot nào đủ khả năng đặt giá
        if (top1 == null) {
            isScanning.set(false); // Release Lock
            return;
        }

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
                isScanning.set(false); // Đang giữ top 1 ở mức giá an toàn -> Release Lock
                return;
            }
        }

        final AutoBid winnerBot = top1;
        log.info("Bot of {} mathematically won. Submitting ASYNC transaction.", winnerBot.getBidder().getUserName());

        // 6. Xử lý Bất đồng bộ (Asynchronous Chain) - Xóa bỏ rủi ro Thread Starvation
        try {
            bidderCtrl.placeBidOnAuction(winnerBot.getBidder(), auction, winnerBot.getMaxBid(), true)
                    .handle((success, ex) -> {
                        // [ARCHITECT FIX]: Bọc toàn bộ Callback vào Try-Catch để phòng thủ việc Thread Pool từ chối lệnh
                        try {
                            if (ex != null) {
                                log.error("Bot Engine execution failed for user {}: {}", winnerBot.getBidder().getUserName(), ex.getMessage());
                                synchronized (botQueue) {
                                    botQueue.removeIf(b -> b.getBidder().getId().equals(winnerBot.getBidder().getId()));
                                }
                                botPool.submit(() -> processNextBot(auction));
                            } else if (Boolean.TRUE.equals(success)) {
                                // Giao dịch DB thành công, luồng hoàn tất -> Release Lock
                                isScanning.set(false);
                            } else {
                                log.info("Bot of {} failed (insufficient balance or DB reject). Removing configuration.", winnerBot.getBidder().getUserName());
                                synchronized (botQueue) {
                                    botQueue.removeIf(b -> b.getBidder().getId().equals(winnerBot.getBidder().getId()));
                                }
                                botPool.submit(() -> processNextBot(auction));
                            }
                        } catch (Exception poolEx) {
                            log.error("Critical fail inside async callback (e.g., Thread Pool full), forcing lock release: {}", poolEx.getMessage());
                            isScanning.set(false);
                        }
                        return null;
                    });
        } catch (Exception e) {
            log.error("Critical fail submitting async bid task: {}", e.getMessage());
            isScanning.set(false); // Đảm bảo không bao giờ bị Deadlock dù lỗi không lường trước
        }
    }
}