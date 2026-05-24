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
 * Standard baseline unit tests for structural {@link Auction} behaviors.
 * Verifies price-increment thresholds, tie-breaking bounds, and transactional bid processing.
 */
class AuctionTest {

    private Auction auction;
    private User seller;
    private User bidder1;
    private User bidder2;
    private Item item;

    @BeforeEach
    void setUp() {
        seller = new User();
        seller.setId("SELLER-1");
        seller.setUserName("Seller");

        bidder1 = new User();
        bidder1.setId("BIDDER-1");
        bidder1.setUserName("Bidder1");

        bidder2 = new User();
        bidder2.setId("BIDDER-2");
        bidder2.setUserName("Bidder2");

        item = new TangibleItem("ITEM-1", "Vintage Watch", "", 1000L);

        auction = new Auction("AUC-1", item, seller, 50L, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(9));
        auction.setStatus(Auction.STATUS_RUNNING);
    }

    @Test
    @DisplayName("Should return valid BidResult for first high bid")
    void testFirstHighBid() {
        long bidAmount = 1500L;
        Auction.BidResult result = auction.calculateBidResult(bidder1, bidAmount);

        assertNotNull(result, "Result should not be null for valid first bid");
        assertEquals(bidder1, result.newWinner, "Bidder1 should be the new winner");
        assertEquals(bidAmount, result.newHighestMaxBid, "Highest max bid should be 1500");
        assertEquals(1000L, result.newCurrentPrice, "First bid current price should be starting price");
    }

    @Test
    @DisplayName("Should return null for bid lower than starting price")
    void testBidLowerThanStartingPrice() {
        long bidAmount = 500L;
        Auction.BidResult result = auction.calculateBidResult(bidder1, bidAmount);

        assertNull(result, "Result should be null for bid below starting price");
    }

    @Test
    @DisplayName("Should correctly outbid existing winner")
    void testOutbidExistingWinner() {
        Auction.BidResult firstResult = auction.calculateBidResult(bidder1, 1500L);
        auction.applyBidResult(bidder1, firstResult);

        long bidAmount = 1600L;
        Auction.BidResult secondResult = auction.calculateBidResult(bidder2, bidAmount);

        assertNotNull(secondResult);
        assertEquals(bidder2, secondResult.newWinner);
        assertEquals(1600L, secondResult.newHighestMaxBid);
        assertEquals(1550L, secondResult.newCurrentPrice);
    }

    @Test
    @DisplayName("Should return null for bid lower than current price + increment")
    void testInsufficientOutbid() {
        Auction.BidResult firstResult = auction.calculateBidResult(bidder1, 1500L);
        auction.applyBidResult(bidder1, firstResult);

        long bidAmount = 1020L;
        Auction.BidResult secondResult = auction.calculateBidResult(bidder2, bidAmount);

        assertNull(secondResult, "Result should be null for bid below currentPrice + increment");
    }

    @Test
    @DisplayName("Should extend end time when bid is placed within last minute (Anti-sniping)")
    void testAntiSniping() {
        LocalDateTime nearEnd = LocalDateTime.now().plusSeconds(30);
        auction.setEndTime(nearEnd);

        Auction.BidResult result = auction.calculateBidResult(bidder1, 2000L);
        assertNotNull(result);

        assertEquals(nearEnd.plusMinutes(2), result.newEndTime, "End time should be exactly 2 minutes later than previous end time");
    }

    @Test
    @DisplayName("Should produce consistent bid results when calculated from the same snapshot (Concurrent Bid)")
    void testConcurrentBidCalculation() {
        Auction.BidResult result1 = auction.calculateBidResult(bidder1, 1500L);
        Auction.BidResult result2 = auction.calculateBidResult(bidder2, 1500L);

        assertNotNull(result1);
        assertNotNull(result2);

        assertEquals(bidder1, result1.newWinner);
        assertEquals(bidder2, result2.newWinner);
        assertEquals(1000L, result1.newCurrentPrice);
        assertEquals(1000L, result2.newCurrentPrice);
        assertEquals(1500L, result1.newHighestMaxBid);
        assertEquals(1500L, result2.newHighestMaxBid);

        auction.applyBidResult(bidder1, result1);

        Auction.BidResult afterApply = auction.calculateBidResult(bidder2, 1500L);
        assertNotNull(afterApply, "Bid is still >= min required, so calculation should succeed");
        assertEquals(bidder1, afterApply.newWinner, "Equal max bid should not dethrone the current winner");
        assertEquals(1500L, afterApply.newCurrentPrice, "Price should rise to the current highest max bid");
    }
}