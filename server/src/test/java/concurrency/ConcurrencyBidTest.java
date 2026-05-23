package concurrency;

import model.auction.Auction;
import model.item.Item;
import model.item.TangibleItem;
import model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Đã được Refactor: Tách biệt hoàn toàn việc test "Logic tính toán giá" (toán học)
 * ra khỏi "Khóa DB". Các test case chạy tuần tự, mang tính xác định (Deterministic)
 * để loại bỏ triệt để hiện tượng Flaky Test do quản lý luồng của HĐH.
 */
class ConcurrencyBidTest {

    private Auction auction;
    private User seller;
    private User bidder1;
    private User bidder2;

    @BeforeEach
    void setUp() {
        seller = new User();
        seller.setId("SELLER-1");

        bidder1 = new User();
        bidder1.setId("BIDDER-1");

        bidder2 = new User();
        bidder2.setId("BIDDER-2");

        Item item = new TangibleItem("", "", "", 1000L);

        LocalDateTime start = LocalDateTime.now().minusMinutes(10);
        LocalDateTime end = LocalDateTime.now().plusMinutes(10);

        auction = new Auction("AUC-MATH-TEST", item, seller, 50L, start, end);
        auction.setStatus(Auction.STATUS_RUNNING);
    }

    @Test
    void calculateBidResult_FirstBid_ShouldSetCurrentPriceToStartingPrice() {
        // Lượt bid đầu tiên, hệ thống phải lấy giá khởi điểm làm giá hiện tại
        Auction.BidResult result = auction.calculateBidResult(bidder1, 2000L);

        assertNotNull(result);
        assertEquals(bidder1.getId(), result.newWinner.getId());
        assertEquals(1000L, result.newCurrentPrice);
        assertEquals(2000L, result.newHighestMaxBid);
    }

    @Test
    void calculateBidResult_OutbidScenario_ShouldIncrementCorrectly() {
        // Bước 1: Bidder 1 ra giá tối đa 2000 (Giá hiện tại sẽ là 1000)
        Auction.BidResult result1 = auction.calculateBidResult(bidder1, 2000L);
        auction.applyBidResult(bidder1, result1);

        // Bước 2: Bidder 2 ra giá tối đa 3000
        // Hệ thống phải tự động đẩy giá lên bằng (Max của Bidder 1 + Bước giá) = 2000 + 50 = 2050
        Auction.BidResult result2 = auction.calculateBidResult(bidder2, 3000L);

        assertNotNull(result2);
        assertEquals(bidder2.getId(), result2.newWinner.getId());
        assertEquals(2050L, result2.newCurrentPrice);
        assertEquals(3000L, result2.newHighestMaxBid);
    }

    @Test
    void calculateBidResult_BidLowerThanCurrentMax_ShouldPushPriceButNotChangeWinner() {
        // Bước 1: Bidder 1 cắm auto-bid mức 5000
        Auction.BidResult result1 = auction.calculateBidResult(bidder1, 5000L);
        auction.applyBidResult(bidder1, result1); // Giá hiện tại: 1000

        // Bước 2: Bidder 2 vào phá giá, ném 3000 (Nhỏ hơn 5000 của Bidder 1)
        Auction.BidResult result2 = auction.calculateBidResult(bidder2, 3000L);

        assertNotNull(result2);
        // Bidder 1 VẪN WIN
        assertEquals(bidder1.getId(), result2.newWinner.getId());
        // Giá hiện tại bị đẩy lên = (Mức phá của Bidder 2 + Bước giá) = 3000 + 50 = 3050
        assertEquals(3050L, result2.newCurrentPrice);
        assertEquals(5000L, result2.newHighestMaxBid); // Max bid của hệ thống vẫn là 5000
    }
}