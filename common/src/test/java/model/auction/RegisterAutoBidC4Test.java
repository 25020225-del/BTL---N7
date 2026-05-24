package model.auction;

import model.item.TangibleItem;
import model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Functional regression verification suite targeting automated proxy bot threshold constraints.
 * Ensures the system boundary rejects sub-optimal limit profiles uniformly to block invalid escrow locks.
 */
@DisplayName("Auction — registerAutoBid C4 Regression Tests")
class RegisterAutoBidC4Test {

    private static final long STARTING_PRICE  = 1_000L;
    private static final long BID_INCREMENT   =   100L;

    private Auction auction;
    private User    seller;
    private User    bidder1;
    private User    bidder2;

    @BeforeEach
    void setUp() {
        seller  = new User("SELLER-1", "seller", "pass", "Seller", "SELLER");
        bidder1 = new User("B-1", "bidder1", "pass", "Bidder 1", "BUYER");
        bidder2 = new User("B-2", "bidder2", "pass", "Bidder 2", "BUYER");

        TangibleItem item = new TangibleItem("ITM-1", "Test Item", "desc", STARTING_PRICE);

        auction = new Auction("AUC-C4", item, seller, BID_INCREMENT,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusHours(1));
        auction.setStatus(Auction.STATUS_RUNNING);
    }

    private void makeWinner() {
        Auction.BidResult result = auction.calculateBidResult(bidder1, STARTING_PRICE + BID_INCREMENT);
        assertNotNull(result);
        auction.applyBidResult(bidder1, result);
    }

    @Test
    @DisplayName("[C4-A1] maxBid == currentPrice → ACCEPTED (Valid first automated bid boundary)")
    void noWinner_maxBidEqualsCurrentPrice_accepted() {
        boolean result = auction.registerAutoBid(bidder1, STARTING_PRICE, BID_INCREMENT);
        assertTrue(result);
    }

    @Test
    @DisplayName("[C4-A2] maxBid < currentPrice → REJECTED")
    void noWinner_maxBidBelowCurrentPrice_rejected() {
        boolean result = auction.registerAutoBid(bidder1, STARTING_PRICE - 1, BID_INCREMENT);
        assertFalse(result);
    }

    @Test
    @DisplayName("[C4-A3] maxBid > currentPrice → ACCEPTED")
    void noWinner_maxBidAboveCurrentPrice_accepted() {
        boolean result = auction.registerAutoBid(bidder1, STARTING_PRICE + BID_INCREMENT, BID_INCREMENT);
        assertTrue(result);
    }

    @Test
    @DisplayName("[C4-B1] maxBid == currentPrice + bidIncrement → ACCEPTED (Minimum execution overhead)")
    void withWinner_maxBidEqualsMinRequired_accepted() {
        makeWinner();
        long minRequired = auction.getCurrentPrice() + auction.getBidIncrement();
        boolean result = auction.registerAutoBid(bidder2, minRequired, BID_INCREMENT);
        assertTrue(result);
    }

    @Test
    @DisplayName("[C4-B2] maxBid == currentPrice + 1 → REJECTED (Sub-increment boundary limit violation)")
    void withWinner_maxBidOneAboveCurrentPrice_rejected() {
        makeWinner();
        long badMaxBid = auction.getCurrentPrice() + 1;
        boolean result = auction.registerAutoBid(bidder2, badMaxBid, BID_INCREMENT);
        assertFalse(result);
    }

    @Test
    @DisplayName("[C4-B3] maxBid < currentPrice → REJECTED")
    void withWinner_maxBidBelowCurrentPrice_rejected() {
        makeWinner();
        boolean result = auction.registerAutoBid(bidder2, auction.getCurrentPrice() - 1, BID_INCREMENT);
        assertFalse(result);
    }

    @Test
    @DisplayName("[C4-B4] maxBid > minRequired → ACCEPTED")
    void withWinner_maxBidAboveMinRequired_accepted() {
        makeWinner();
        long goodMaxBid = auction.getCurrentPrice() + auction.getBidIncrement() * 5;
        boolean result = auction.registerAutoBid(bidder2, goodMaxBid, BID_INCREMENT);
        assertTrue(result);
    }

    @Test
    @DisplayName("[C4-C1] getMinAutoBidRequired() == currentPrice (Uncontested baseline evaluation)")
    void getMinRequired_noWinner_returnsCurrentPrice() {
        assertEquals(STARTING_PRICE, auction.getMinAutoBidRequired());
    }

    @Test
    @DisplayName("[C4-C2] getMinAutoBidRequired() == currentPrice + bidIncrement (Contested baseline evaluation)")
    void getMinRequired_withWinner_returnsCurrentPluIncrement() {
        makeWinner();
        long expected = auction.getCurrentPrice() + BID_INCREMENT;
        assertEquals(expected, auction.getMinAutoBidRequired());
    }

    @Test
    @DisplayName("[C4-D1] Auction FINISHED → registerAutoBid always returns false")
    void finishedAuction_alwaysRejected() {
        auction.setStatus(Auction.STATUS_FINISHED);
        boolean result = auction.registerAutoBid(bidder1, STARTING_PRICE * 10, BID_INCREMENT);
        assertFalse(result);
    }

    @Test
    @DisplayName("[C4-D2] Auction WAITING_FOR_BID → ACCEPTED under boundary rules")
    void waitingAuction_validMaxBid_accepted() {
        auction.setStatus(Auction.STATUS_WAITING_FOR_BID);
        boolean result = auction.registerAutoBid(bidder1, STARTING_PRICE, BID_INCREMENT);
        assertTrue(result);
    }
}