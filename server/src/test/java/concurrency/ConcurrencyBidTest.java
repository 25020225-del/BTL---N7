package concurrency;

import model.auction.Auction;
import model.item.Item;
import model.user.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress-test {@link Auction#calculateBidResult} + {@link Auction#applyBidResult}
 * under many threads sharing one {@link Auction}.
 * <p>
 * A intrinsic lock protects the mutable auction so contention is orderly; recorded index order
 * is replayed on a cloned auction offline to prove no torn updates versus the deterministic model.
 */
class ConcurrencyBidTest {

    private static Auction baselineAuction(Item item, User seller) {
        // Fixed window so subject vs replay share identical timelines (avoids flaky anti-sniping / now() coupling).
        LocalDateTime start = LocalDateTime.of(2030, 6, 1, 10, 0, 0);
        LocalDateTime end = LocalDateTime.of(2030, 12, 1, 23, 0, 0);
        Auction a = new Auction("AUC-CONC-STRESS", item, seller, 50.0, start, end);
        a.setStatus(Auction.STATUS_RUNNING);
        return a;
    }

    private static double bidForIndex(int idx) {
        return 2000.0 + idx * 25.0;
    }

    @Test
    void hundredThreads_bidResultsMatchDeterministicReplay() throws Exception {
        User seller = new User();
        seller.setId("SELLER-C");

        Item item = new Item();
        item.setStartingPrice(1000.0);

        List<User> bidders = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            User u = new User();
            u.setId("B-C-" + i);
            u.setUserName("bidder-" + i);
            bidders.add(u);
        }

        Auction subject = baselineAuction(item, seller);

        List<Integer> applyOrder = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(100);

        for (int t = 0; t < 100; t++) {
            final int idx = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    synchronized (subject) {
                        Auction.BidResult result = subject.calculateBidResult(bidders.get(idx), bidForIndex(idx));
                        if (result != null) {
                            applyOrder.add(idx);
                            subject.applyBidResult(bidders.get(idx), result);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        assertTrue(doneLatch.await(120, TimeUnit.SECONDS), "All workers should finish");
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        Auction reference = baselineAuction(copyItemBaseline(item), seller);
        List<Integer> orderCopy = new ArrayList<>(applyOrder);
        for (Integer idx : orderCopy) {
            Auction.BidResult rr = reference.calculateBidResult(bidders.get(idx), bidForIndex(idx));
            assertNotNull(rr, () -> "Replay step idx=" + idx + " should mirror a valid concurrent step");
            reference.applyBidResult(bidders.get(idx), rr);
        }

        assertNotNull(subject.getWinningBidder(), "Concurrent run should leave a single leader");
        assertEquals(subject.getWinningBidder().getId(), reference.getWinningBidder().getId());
        assertEquals(subject.getCurrentPrice(), reference.getCurrentPrice(), 1e-9);
        assertEquals(subject.getHighestMaxBid(), reference.getHighestMaxBid(), 1e-9);
        assertEquals(subject.getEndTime(), reference.getEndTime(), "endTime must match replay (same bid order, fixed auction window)");
    }

    private static Item copyItemBaseline(Item source) {
        Item copy = new Item();
        copy.setStartingPrice(source.getStartingPrice());
        return copy;
    }
}
