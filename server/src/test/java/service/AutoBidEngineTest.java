package service;

import controller.ServerBidderController;
import database.dao.BidDAO;
import model.auction.Auction;
import model.auction.AutoBid;
import model.item.Item;
import model.item.TangibleItem;
import model.user.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AutoBidEngineTest {

    static final class CapturingBidderController extends ServerBidderController {
        private final AtomicReference<User> lastBidder = new AtomicReference<>();
        private final AtomicReference<Long> lastMaxBid = new AtomicReference<>();

        CapturingBidderController() {
            super(new BidDAO());
        }

        @Override
        public CompletableFuture<Boolean> placeBidOnAuction(User currentUser, Auction auction, long newMaxBid, boolean isBot) {
            lastBidder.set(currentUser);
            lastMaxBid.set(newMaxBid);
            return CompletableFuture.completedFuture(true);
        }
    }

    @Test
    void autoBidEngine_prefersEarlierRegistrationWhenMaxBidTied() throws Exception {
        // Arrange
        Item item = new TangibleItem("", "", "", 1000L);

        User seller = new User();
        seller.setId("SELLER-AE");

        Auction auction = new Auction("AUC-AE", item, seller, 50L,
                LocalDateTime.now().minusSeconds(5),
                LocalDateTime.now().plusMinutes(10));
        auction.setStatus(Auction.STATUS_RUNNING);

        User u1 = new User();
        u1.setId("U1");
        u1.setUserName("u1");

        User u2 = new User();
        u2.setId("U2");
        u2.setUserName("u2");

        AutoBid b1 = new AutoBid(u1, 2000L, 50L);
        AutoBid b2 = new AutoBid(u2, 2000L, 50L);
        b1.setTimeRegistered(LocalDateTime.now().minusSeconds(10)); // earlier
        b2.setTimeRegistered(LocalDateTime.now().minusSeconds(5));  // later

        auction.getActiveAutoBids().offer(b2);
        auction.getActiveAutoBids().offer(b1);

        CapturingBidderController ctrl = new CapturingBidderController();
        AutoBidEngine.setBidderController(ctrl);

        // Act: Gọi trực tiếp hàm đã được mở quyền package-private
        AutoBidEngine.processNextBot(auction);

        // Assert
        assertNotNull(ctrl.lastBidder.get(), "Engine should submit exactly one bid");
        assertEquals("U1", ctrl.lastBidder.get().getId(), "Earlier-registered bot should win tie on maxBid");
        assertEquals(2000L, ctrl.lastMaxBid.get(), "Tie on maxBid should push to maxBid");
    }
}