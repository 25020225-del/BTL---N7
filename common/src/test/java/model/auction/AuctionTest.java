package model.auction;

import model.item.Item;
import model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

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

        item = new Item();
        item.setId("ITEM-1");
        item.setItemName("Vintage Watch");
        item.setStartingPrice(1000.0);

        // Create an auction starting now and ending in 10 minutes
        auction = new Auction(
                "AUC-1",
                item,
                seller,
                50.0, // bidIncrement
                LocalDateTime.now().minusMinutes(1), // started 1 min ago
                LocalDateTime.now().plusMinutes(9)    // ends in 9 mins
        );
        auction.setStatus(Auction.STATUS_RUNNING);
    }

    @Test
    @DisplayName("Should return valid BidResult for first high bid")
    void testFirstHighBid() {
        double bidAmount = 1500.0;
        Auction.BidResult result = auction.calculateBidResult(bidder1, bidAmount);

        assertNotNull(result, "Result should not be null for valid first bid");
        assertEquals(bidder1, result.newWinner, "Bidder1 should be the new winner");
        assertEquals(bidAmount, result.newHighestMaxBid, "Highest max bid should be 1500");
        assertEquals(1000.0, result.newCurrentPrice, "First bid current price should be starting price");
    }

    @Test
    @DisplayName("Should return null for bid lower than starting price")
    void testBidLowerThanStartingPrice() {
        double bidAmount = 500.0;
        Auction.BidResult result = auction.calculateBidResult(bidder1, bidAmount);

        assertNull(result, "Result should be null for bid below starting price");
    }

    @Test
    @DisplayName("Should correctly outbid existing winner")
    void testOutbidExistingWinner() {
        // First bid by Bidder1
        Auction.BidResult firstResult = auction.calculateBidResult(bidder1, 1500.0);
        auction.applyBidResult(bidder1, firstResult);

        // Second bid by Bidder2
        double bidAmount = 1600.0;
        Auction.BidResult secondResult = auction.calculateBidResult(bidder2, bidAmount);

        assertNotNull(secondResult);
        assertEquals(bidder2, secondResult.newWinner);
        assertEquals(1600.0, secondResult.newHighestMaxBid);
        // Current price should be (previous highest max bid + increment) = 1500 + 50 = 1550
        assertEquals(1550.0, secondResult.newCurrentPrice);
    }

    @Test
    @DisplayName("Should return null for bid lower than current price + increment")
    void testInsufficientOutbid() {
        // First bid by Bidder1
        Auction.BidResult firstResult = auction.calculateBidResult(bidder1, 1500.0);
        auction.applyBidResult(bidder1, firstResult);

        // Current price is 1000, increment is 50. Min required is 1050.
        // But Bidder1 has a max bid of 1500.
        // If Bidder2 bids 1200, they are outbidding the CURRENT price (1000) but NOT the max bid (1500).
        // However, the rule says: minRequiredBid = currentPrice + bidIncrement.
        // So 1200 is technically a "valid" attempt to raise the price.
        
        double bidAmount = 1020.0; // Lower than currentPrice (1000) + increment (50)
        Auction.BidResult secondResult = auction.calculateBidResult(bidder2, bidAmount);

        assertNull(secondResult, "Result should be null for bid below currentPrice + increment");
    }

    @Test
    @DisplayName("Should extend end time when bid is placed within last minute (Anti-sniping)")
    void testAntiSniping() {
        // Set end time to 30 seconds from now
        LocalDateTime nearEnd = LocalDateTime.now().plusSeconds(30);
        auction.setEndTime(nearEnd);

        // Place a valid bid
        Auction.BidResult result = auction.calculateBidResult(bidder1, 2000.0);
        assertNotNull(result);
        
        // Anti-sniping in Auction.calculateBidResult(): newEndTime = oldEndTime.plusMinutes(2) (hard-capped by maxEndTime)
        assertEquals(nearEnd.plusMinutes(2), result.newEndTime, "End time should be exactly 2 minutes later than previous end time");
    }

    @Test
    @DisplayName("Should produce consistent bid results when calculated from the same snapshot (Concurrent Bid)")
    void testConcurrentBidCalculation() {
        // Simulate a scenario where two bidders bid the same amount simultaneously
        // based on the same current price.
        
        // Bidder 1 bids 1500
        Auction.BidResult result1 = auction.calculateBidResult(bidder1, 1500.0);
        
        // Bidder 2 also bids 1500 at the same time (before result1 is applied)
        Auction.BidResult result2 = auction.calculateBidResult(bidder2, 1500.0);
        
        assertNotNull(result1);
        assertNotNull(result2);
        
        // From the same snapshot (no winner yet), each bidder becomes the provisional winner of their own calculation.
        assertEquals(bidder1, result1.newWinner);
        assertEquals(bidder2, result2.newWinner);
        assertEquals(1000.0, result1.newCurrentPrice);
        assertEquals(1000.0, result2.newCurrentPrice);
        assertEquals(1500.0, result1.newHighestMaxBid);
        assertEquals(1500.0, result2.newHighestMaxBid);

        // Once one result is applied, recalculating with the same bid amount should reflect the updated auction state.
        auction.applyBidResult(bidder1, result1);

        Auction.BidResult afterApply = auction.calculateBidResult(bidder2, 1500.0);
        assertNotNull(afterApply, "Bid is still >= min required, so calculation should succeed");
        assertEquals(bidder1, afterApply.newWinner, "Equal max bid should not dethrone the current winner");
        assertEquals(1500.0, afterApply.newCurrentPrice, "Price should rise to the current highest max bid");
    }
}
