package concurrency;

import model.auction.Auction;
import model.item.Item;
import model.item.TangibleItem;
import model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test class for validating single-threaded auction bidding logic.
 * Focuses strictly on pricing calculations, automated step-increments, and state updates
 * to ensure deterministic behavior isolated from database locking mechanisms.
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

        auction = new Auction("AUC-MATH-TEST", item, seller, 50L, start, end, 20);
        auction.setStatus(Auction.STATUS_RUNNING);
    }

    @Test
    void calculateBidResult_FirstBid_ShouldSetCurrentPriceToStartingPrice() {
        Auction.BidResult result = auction.calculateBidResult(bidder1, 2000L);

        assertNotNull(result);
        assertEquals(bidder1.getId(), result.newWinner.getId());
        assertEquals(1000L, result.newCurrentPrice);
        assertEquals(2000L, result.newHighestMaxBid);
    }

    @Test
    void calculateBidResult_OutbidScenario_ShouldIncrementCorrectly() {
        Auction.BidResult result1 = auction.calculateBidResult(bidder1, 2000L);
        auction.applyBidResult(bidder1, result1);

        Auction.BidResult result2 = auction.calculateBidResult(bidder2, 3000L);

        assertNotNull(result2);
        assertEquals(bidder2.getId(), result2.newWinner.getId());
        assertEquals(2050L, result2.newCurrentPrice);
        assertEquals(3000L, result2.newHighestMaxBid);
    }

    @Test
    void calculateBidResult_BidLowerThanCurrentMax_ShouldPushPriceButNotChangeWinner() {
        Auction.BidResult result1 = auction.calculateBidResult(bidder1, 5000L);
        auction.applyBidResult(bidder1, result1);

        Auction.BidResult result2 = auction.calculateBidResult(bidder2, 3000L);

        assertNotNull(result2);
        assertEquals(bidder1.getId(), result2.newWinner.getId());
        assertEquals(3050L, result2.newCurrentPrice);
        assertEquals(5000L, result2.newHighestMaxBid);
    }
}