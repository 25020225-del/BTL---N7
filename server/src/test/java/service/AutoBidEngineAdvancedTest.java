package service;

import controller.ServerBidderController;
import model.auction.Auction;
import model.auction.AutoBid;
import model.item.Item;
import model.item.TangibleItem;
import model.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Advanced integration test suite for {@link AutoBidEngine}.
 * Verifies asynchronous recursive execution pathways, concurrent multi-bot contentions,
 * and memory safety patterns regarding state lock releases.
 */
class AutoBidEngineAdvancedTest {

    private ServerBidderController mockBidderCtrl;
    private Auction auction;
    private Field activeScansField;

    @BeforeEach
    void setUp() throws Exception {
        mockBidderCtrl = mock(ServerBidderController.class);
        AutoBidEngine.setBidderController(mockBidderCtrl);

        User seller = new User();
        seller.setId("SELLER-TEST");
        Item item = new TangibleItem("", "", "", 1000L);

        auction = new Auction("AUC-DEADLOCK-TEST", item, seller, 50L,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusMinutes(10));
        auction.setStatus(Auction.STATUS_RUNNING);

        activeScansField = AutoBidEngine.class.getDeclaredField("activeScans");
        activeScansField.setAccessible(true);
    }

    @AfterEach
    @SuppressWarnings("unchecked")
    void tearDown() throws Exception {
        ConcurrentHashMap<String, AtomicBoolean> scans =
                (ConcurrentHashMap<String, AtomicBoolean>) activeScansField.get(null);
        scans.clear();
    }

    @SuppressWarnings("unchecked")
    private void waitForLockRelease(String auctionId) throws InterruptedException, IllegalAccessException {
        ConcurrentHashMap<String, AtomicBoolean> scans =
                (ConcurrentHashMap<String, AtomicBoolean>) activeScansField.get(null);
        AtomicBoolean isScan = scans.get(auctionId);
        int waitTime = 0;

        // Polls the state map periodically to account for async assertion execution latencies
        while (isScan != null && isScan.get() && waitTime < 3000) {
            Thread.sleep(50);
            waitTime += 50;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void processNextBot_With3Bots_ShouldCalculateWinnerAndExecuteOnce() throws Exception {
        User u1 = new User();
        u1.setId("BOT-1");
        User u2 = new User();
        u2.setId("BOT-2");
        User u3 = new User();
        u3.setId("BOT-3");

        AutoBid b1 = new AutoBid(u1, 2000L, 50L);
        AutoBid b2 = new AutoBid(u2, 3000L, 50L);
        AutoBid b3 = new AutoBid(u3, 2500L, 50L);

        auction.getActiveAutoBids().offer(b1);
        auction.getActiveAutoBids().offer(b2);
        auction.getActiveAutoBids().offer(b3);

        when(mockBidderCtrl.placeBidOnAuction(any(User.class), eq(auction), anyLong(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(true));

        AutoBidEngine.triggerBotScan(auction);

        verify(mockBidderCtrl, timeout(3000).times(1))
                .placeBidOnAuction(any(User.class), eq(auction), anyLong(), eq(true));

        waitForLockRelease(auction.getId());
        ConcurrentHashMap<String, AtomicBoolean> scans =
                (ConcurrentHashMap<String, AtomicBoolean>) activeScansField.get(null);

        assertFalse(scans.get(auction.getId()).get(), "NGUY HIỂM: isScanning chưa được reset về false!");
        assertEquals(1, auction.getActiveAutoBids().size(), "Hàng đợi RAM chưa dọn sạch bot thua cuộc");
        assertEquals("BOT-2", auction.getActiveAutoBids().peek().getBidder().getId(), "Bot chiến thắng không chính xác");
    }

    @Test
    @SuppressWarnings("unchecked")
    void processNextBot_WhenControllerThrowsException_ShouldStillReleaseLock() throws Exception {
        User u1 = new User();
        u1.setId("BOT-1");
        AutoBid b1 = new AutoBid(u1, 2000L, 50L);
        auction.getActiveAutoBids().offer(b1);

        CompletableFuture<Boolean> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Simulated Database Crash"));

        when(mockBidderCtrl.placeBidOnAuction(any(User.class), eq(auction), anyLong(), anyBoolean()))
                .thenReturn(failedFuture);

        AutoBidEngine.triggerBotScan(auction);

        waitForLockRelease(auction.getId());

        ConcurrentHashMap<String, AtomicBoolean> scans =
                (ConcurrentHashMap<String, AtomicBoolean>) activeScansField.get(null);

        assertFalse(scans.get(auction.getId()).get(), "NGUY HIỂM: Lỗi hệ thống đã xảy ra nhưng isScanning không được reset!");
    }
}