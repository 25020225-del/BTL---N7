package model.auction;

import model.item.Item;
import model.item.TangibleItem;
import model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho cơ chế thời gian mới của Auction:
 *   - WAITING_FOR_BID: endTime = null, chờ bid đầu tiên
 *   - Bid đầu tiên kích hoạt đồng hồ
 *   - Anti-sniping vô hạn lần (không hard-cap)
 *   - Xoá maxEndTime
 */
@DisplayName("Auction – New Timer Logic Tests")
class AuctionNewTimerTest {

    private static final int DURATION = 30; // phút

    private Auction auction;
    private User    seller;
    private User    bidder1;
    private User    bidder2;
    private Item    item;

    @BeforeEach
    void setUp() {
        seller  = new User("S-1", "seller",  "pass", "Seller",   "SELLER");
        bidder1 = new User("B-1", "bidder1", "pass", "Bidder 1", "USER");
        bidder2 = new User("B-2", "bidder2", "pass", "Bidder 2", "USER");

        seller.setGood(true);

        item = new TangibleItem("", "Test Item", "", 1000L);

        // createNewAuction: endTime = null, status = OPEN (seller isGood)
        auction = Auction.createNewAuction(
                item, seller, 50L,
                LocalDateTime.now().minusMinutes(5),  // startTime đã qua
                DURATION);

        // Giả lập Monitor đã chuyển OPEN → WAITING_FOR_BID
        auction.setStatus(Auction.STATUS_WAITING_FOR_BID);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Khởi tạo
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createNewAuction: endTime = null, durationMinutes lưu đúng")
    void createNewAuction_endTimeNullAndDurationStored() {
        Auction fresh = Auction.createNewAuction(
                item, seller, 50L, LocalDateTime.now(), DURATION);

        assertNull(fresh.getEndTime(),
                "endTime phải null khi mới tạo (chờ bid đầu tiên)");
        assertEquals(DURATION, fresh.getDurationMinutes(),
                "durationMinutes phải được lưu đúng");
        assertEquals(Auction.STATUS_OPEN, fresh.getStatus(),
                "Seller isGood → trạng thái ban đầu phải là OPEN");
    }

    @Test
    @DisplayName("Auction của seller không uy tín: status = PENDING_APPROVAL")
    void createNewAuction_sellerNotGood_statusPending() {
        User badSeller = new User("S-2", "bad", "pass", "Bad Seller", "SELLER");
        badSeller.setGood(false);

        Auction pending = Auction.createNewAuction(item, badSeller, 50L,
                LocalDateTime.now(), DURATION);
        assertEquals(Auction.STATUS_PENDING, pending.getStatus());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. calculateBidResult – trạng thái WAITING_FOR_BID
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Bid đầu tiên khi WAITING_FOR_BID: BidResult.isFirstBid = true")
    void firstBid_setsIsFirstBidTrue() {
        Auction.BidResult result = auction.calculateBidResult(bidder1, 1500L);

        assertNotNull(result, "Bid hợp lệ phải trả về BidResult");
        assertTrue(result.isFirstBid, "Bid đầu tiên phải có isFirstBid = true");
    }

    @Test
    @DisplayName("Bid đầu tiên: endTime = now + durationMinutes (±2 giây)")
    void firstBid_endTimeSetToNowPlusDuration() {
        LocalDateTime before = LocalDateTime.now();
        Auction.BidResult result = auction.calculateBidResult(bidder1, 1500L);
        LocalDateTime after = LocalDateTime.now();

        assertNotNull(result);

        LocalDateTime expectedMin = before.plusMinutes(DURATION);
        LocalDateTime expectedMax = after.plusMinutes(DURATION);

        assertFalse(result.newEndTime.isBefore(expectedMin),
                "endTime phải >= now_before + duration");
        assertFalse(result.newEndTime.isAfter(expectedMax),
                "endTime phải <= now_after + duration");
    }

    @Test
    @DisplayName("Bid đầu tiên: applyBidResult chuyển status sang RUNNING")
    void firstBid_applyResult_transitionsToRunning() {
        Auction.BidResult result = auction.calculateBidResult(bidder1, 1500L);
        assertNotNull(result);

        auction.applyBidResult(bidder1, result);

        assertEquals(Auction.STATUS_RUNNING, auction.getStatus(),
                "Sau bid đầu tiên, status phải là RUNNING");
        assertNotNull(auction.getEndTime(),
                "Sau bid đầu tiên, endTime không còn null");
    }

    @Test
    @DisplayName("Bid với mức thấp hơn giá khởi điểm: trả về null")
    void firstBid_belowStartingPrice_returnsNull() {
        Auction.BidResult result = auction.calculateBidResult(bidder1, 500L);
        assertNull(result, "Bid dưới giá khởi điểm phải bị từ chối");
    }

    @Test
    @DisplayName("Bid khi status = OPEN: bị từ chối (chưa đến lượt WAITING)")
    void bid_whenStatusOpen_rejected() {
        auction.setStatus(Auction.STATUS_OPEN);
        Auction.BidResult result = auction.calculateBidResult(bidder1, 1500L);
        assertNull(result, "Không thể bid khi status còn là OPEN");
    }

    @Test
    @DisplayName("Bid khi status = FINISHED: bị từ chối")
    void bid_whenStatusFinished_rejected() {
        auction.setStatus(Auction.STATUS_FINISHED);
        Auction.BidResult result = auction.calculateBidResult(bidder1, 1500L);
        assertNull(result, "Không thể bid khi phiên đã kết thúc");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Anti-sniping – vô hạn lần, không hard-cap
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Anti-sniping: gia hạn đúng 2 phút khi bid trong 60 giây cuối")
    void antiSniping_extendsBy2Minutes() {
        // Kích hoạt phiên với bid đầu tiên
        activateAuction();

        // Đặt endTime còn 30 giây
        LocalDateTime nearEnd = LocalDateTime.now().plusSeconds(30);
        auction.setEndTime(nearEnd);

        Auction.BidResult result = auction.calculateBidResult(bidder2, 2000L);
        assertNotNull(result);
        assertFalse(result.isFirstBid, "Đây không phải bid đầu tiên");

        LocalDateTime expected = nearEnd.plusSeconds(Auction.ANTI_SNIPING_EXTENSION_SECONDS);
        assertEquals(expected, result.newEndTime,
                "endTime phải được gia hạn đúng 2 phút từ endTime cũ");
    }

    @Test
    @DisplayName("Anti-sniping: gia hạn vô hạn lần, lần thứ 10 vẫn hoạt động")
    void antiSniping_unlimitedExtensions() {
        activateAuction();

        LocalDateTime expectedEnd = auction.getEndTime();

        for (int i = 1; i <= 10; i++) {
            // Đẩy endTime còn 30 giây trước mỗi bid
            LocalDateTime nearEnd = LocalDateTime.now().plusSeconds(30);
            auction.setEndTime(nearEnd);

            long bidAmount = auction.getCurrentPrice() + auction.getBidIncrement();
            User currentBidder = (i % 2 == 0) ? bidder1 : bidder2;
            Auction.BidResult result = auction.calculateBidResult(currentBidder, bidAmount + 1000L);

            assertNotNull(result, "Lần gia hạn " + i + ": BidResult không được null");
            assertFalse(result.isFirstBid);

            // Mỗi lần gia hạn đúng 2 phút so với nearEnd
            assertEquals(nearEnd.plusSeconds(Auction.ANTI_SNIPING_EXTENSION_SECONDS),
                    result.newEndTime, "Lần gia hạn " + i + ": endTime không đúng");

            auction.applyBidResult(currentBidder, result);
        }

        // Kiểm tra đồng hồ vẫn chạy (không bị hard-cap dừng lại)
        assertEquals(Auction.STATUS_RUNNING, auction.getStatus(),
                "Sau 10 lần gia hạn, phiên vẫn phải RUNNING");
    }

    @Test
    @DisplayName("Anti-sniping: không gia hạn khi bid cách endTime hơn 60 giây")
    void antiSniping_noExtensionWhenMoreThan60SecsRemaining() {
        activateAuction();

        LocalDateTime farEnd = LocalDateTime.now().plusMinutes(5); // 5 phút còn lại
        auction.setEndTime(farEnd);

        Auction.BidResult result = auction.calculateBidResult(bidder2, 2000L);
        assertNotNull(result);

        assertEquals(farEnd, result.newEndTime,
                "endTime không được gia hạn khi còn hơn 60 giây");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. maxEndTime đã bị xoá hoàn toàn
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Không còn trường maxEndTime trong Auction")
    void noMaxEndTimeField() throws NoSuchMethodException {
        // Verify getMaxEndTime() và setMaxEndTime() không còn tồn tại
        boolean hasGetter = java.util.Arrays.stream(Auction.class.getMethods())
                .anyMatch(m -> m.getName().equals("getMaxEndTime"));
        boolean hasSetter = java.util.Arrays.stream(Auction.class.getMethods())
                .anyMatch(m -> m.getName().equals("setMaxEndTime"));

        assertFalse(hasGetter, "getMaxEndTime() phải đã bị xoá");
        assertFalse(hasSetter, "setMaxEndTime() phải đã bị xoá");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. closeAuctionIfTimeIsUp
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("closeAuctionIfTimeIsUp: WAITING_FOR_BID không bị đóng")
    void closeIfTimeUp_waitingForBid_notClosed() {
        assertEquals(Auction.STATUS_WAITING_FOR_BID, auction.getStatus());
        assertNull(auction.getEndTime());

        auction.closeAuctionIfTimeIsUp(); // không nên throw hoặc đóng

        assertEquals(Auction.STATUS_WAITING_FOR_BID, auction.getStatus(),
                "WAITING_FOR_BID không được bị đóng bởi closeAuctionIfTimeIsUp");
    }

    @Test
    @DisplayName("closeAuctionIfTimeIsUp: RUNNING với endTime đã qua → FINISHED")
    void closeIfTimeUp_running_endTimePast_becomesFinished() {
        activateAuction();
        auction.setEndTime(LocalDateTime.now().minusSeconds(1)); // endTime đã qua

        auction.closeAuctionIfTimeIsUp();

        assertEquals(Auction.STATUS_FINISHED, auction.getStatus());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. revertLastBid – nhận thêm previousEndTime và previousStatus
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("revertLastBid: khôi phục đúng endTime và status")
    void revertLastBid_restoresEndTimeAndStatus() {
        activateAuction();

        LocalDateTime endBefore = auction.getEndTime();
        String statusBefore = auction.getStatus();

        // Bid của bidder2 thắng
        Auction.BidResult result = auction.calculateBidResult(bidder2, 2000L);
        assertNotNull(result);

        User prevWinner = auction.getWinningBidder();
        long prevMaxBid = auction.getHighestMaxBid();
        model.finance.BidTransaction txn = auction.applyBidResult(bidder2, result);

        // Giả lập rollback
        auction.revertLastBid(prevWinner, prevMaxBid, endBefore, statusBefore, txn);

        assertEquals(endBefore, auction.getEndTime(),
                "endTime phải được khôi phục sau revert");
        assertEquals(statusBefore, auction.getStatus(),
                "status phải được khôi phục sau revert");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    /** Kích hoạt phiên bằng bid đầu tiên hợp lệ từ bidder1. */
    private void activateAuction() {
        Auction.BidResult firstResult = auction.calculateBidResult(bidder1, 1500L);
        assertNotNull(firstResult, "Bid đầu tiên phải hợp lệ");
        auction.applyBidResult(bidder1, firstResult);
        assertEquals(Auction.STATUS_RUNNING, auction.getStatus());
        assertNotNull(auction.getEndTime());
    }
}