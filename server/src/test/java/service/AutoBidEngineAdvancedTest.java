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
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Lớp kiểm thử nâng cao dành riêng cho AutoBidEngine.
 * Tập trung vào luồng đệ quy bất đồng bộ, tình huống tranh chấp nhiều Bot,
 * và đảm bảo an toàn bộ nhớ (giải phóng Lock).
 */
class AutoBidEngineAdvancedTest {

    private ServerBidderController mockBidderCtrl;
    private Auction auction;
    private Field activeScansField;

    @BeforeEach
    void setUp() throws Exception {
        // 1. Mock Controller để không chọc xuống Database thật
        mockBidderCtrl = mock(ServerBidderController.class);
        AutoBidEngine.setBidderController(mockBidderCtrl);

        // 2. Setup dữ liệu phiên đấu giá ảo
        User seller = new User();
        seller.setId("SELLER-TEST");
        Item item = new TangibleItem("", "", "", 1000L);

        auction = new Auction("AUC-DEADLOCK-TEST", item, seller, 50L,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusMinutes(10));
        auction.setStatus(Auction.STATUS_RUNNING);

        // 3. Chuẩn bị Reflection để soi biến private static "activeScans"
        activeScansField = AutoBidEngine.class.getDeclaredField("activeScans");
        activeScansField.setAccessible(true);
    }

    @AfterEach
    @SuppressWarnings("unchecked")
    void tearDown() throws Exception {
        // Dọn dẹp Map Lock sau mỗi Test Case để tránh Test Pollution
        ConcurrentHashMap<String, AtomicBoolean> scans =
                (ConcurrentHashMap<String, AtomicBoolean>) activeScansField.get(null);
        scans.clear();
    }

    // [ARCHITECT FIX]: Hàm hỗ trợ luồng JUnit chủ động chờ luồng Async xử lý xong (Tối đa 3 giây)
    @SuppressWarnings("unchecked")
    private void waitForLockRelease(String auctionId) throws InterruptedException, IllegalAccessException {
        ConcurrentHashMap<String, AtomicBoolean> scans =
                (ConcurrentHashMap<String, AtomicBoolean>) activeScansField.get(null);
        AtomicBoolean isScan = scans.get(auctionId);
        int waitTime = 0;
        while (isScan != null && isScan.get() && waitTime < 3000) {
            Thread.sleep(50);
            waitTime += 50;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void processNextBot_With3Bots_ShouldCalculateWinnerAndExecuteOnce() throws Exception {
        // Arrange: Tạo 3 chiến thần AutoBid với các mức giá giằng co
        User u1 = new User(); u1.setId("BOT-1");
        User u2 = new User(); u2.setId("BOT-2");
        User u3 = new User(); u3.setId("BOT-3");

        AutoBid b1 = new AutoBid(u1, 2000L, 50L);
        AutoBid b2 = new AutoBid(u2, 3000L, 50L);
        AutoBid b3 = new AutoBid(u3, 2500L, 50L);

        auction.getActiveAutoBids().offer(b1);
        auction.getActiveAutoBids().offer(b2);
        auction.getActiveAutoBids().offer(b3);

        // Giả lập Controller báo đặt giá thành công
        when(mockBidderCtrl.placeBidOnAuction(any(User.class), eq(auction), anyLong(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(true));

        // Act: Kích hoạt quét Bot
        AutoBidEngine.triggerBotScan(auction);

        // Assert 1: Xác nhận sự tối ưu hóa của thuật toán
        // Chỉ gọi Database chính xác 1 lần cho người chiến thắng cuối cùng (BOT-2)
        verify(mockBidderCtrl, timeout(3000).times(1))
                .placeBidOnAuction(any(User.class), eq(auction), anyLong(), eq(true));

        // Assert 2: Phải chắc chắn Lock đã được nhả ra
        waitForLockRelease(auction.getId());
        ConcurrentHashMap<String, AtomicBoolean> scans =
                (ConcurrentHashMap<String, AtomicBoolean>) activeScansField.get(null);

        assertFalse(scans.get(auction.getId()).get(), "NGUY HIỂM: isScanning chưa được reset về false!");

        // Assert 3: Các bot thua cuộc (BOT-1, BOT-3) đã bị dọn dẹp, chỉ còn chiến thần top 1
        assertEquals(1, auction.getActiveAutoBids().size(), "Hàng đợi RAM chưa dọn sạch bot thua cuộc");
        assertEquals("BOT-2", auction.getActiveAutoBids().peek().getBidder().getId(), "Bot chiến thắng không chính xác");
    }

    @Test
    @SuppressWarnings("unchecked")
    void processNextBot_WhenControllerThrowsException_ShouldStillReleaseLock() throws Exception {
        // Arrange: Kịch bản rủi ro - Controller văng lỗi
        User u1 = new User(); u1.setId("BOT-1");
        AutoBid b1 = new AutoBid(u1, 2000L, 50L);
        auction.getActiveAutoBids().offer(b1);

        // Ép Controller ném lỗi khi CompletableFuture thực thi
        CompletableFuture<Boolean> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Simulated Database Crash"));

        when(mockBidderCtrl.placeBidOnAuction(any(User.class), eq(auction), anyLong(), anyBoolean()))
                .thenReturn(failedFuture);

        // Act
        AutoBidEngine.triggerBotScan(auction);

        // Assert: Chờ luồng đệ quy xử lý khối Catch và nhả Lock
        waitForLockRelease(auction.getId());

        ConcurrentHashMap<String, AtomicBoolean> scans =
                (ConcurrentHashMap<String, AtomicBoolean>) activeScansField.get(null);

        assertFalse(scans.get(auction.getId()).get(), "NGUY HIỂM: Lỗi hệ thống đã xảy ra nhưng isScanning không được reset!");
    }
}