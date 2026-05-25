package concurrency;

import controller.ServerBidderController;
import database.DatabaseManager;
import database.dao.AuctionDAO;
import database.dao.BidDAO;
import database.dao.WalletDAO;
import model.auction.Auction;
import model.item.Item;
import model.item.TangibleItem;
import model.user.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QA/QC Unit & Integration Test Suite validating core bidding business transactions
 * and optimistic concurrency control mechanisms.
 */
class BiddingFlowTest {

    private static WalletDAO walletDAO;
    private static AuctionDAO auctionDAO;
    private static BidDAO bidDAO;
    private static ServerBidderController controller;

    @BeforeAll
    static void initDb() {
        DatabaseManager.initializeDatabase();
        walletDAO = new WalletDAO();
        auctionDAO = new AuctionDAO();
        bidDAO = new BidDAO();
        controller = new ServerBidderController(bidDAO);
        service.AutoBidEngine.setBidderController(controller);
    }

    /**
     * Test Case for EC-201: The Auto-Bid Cancellation Vulnerability.
     * Simulates User A setting an auto-bid -> Leading -> Cancelling the auto-bid (wallet remains locked)
     * -> User B outbids User A -> Asserts that User A's locked balance is fully refunded.
     */
    @Test
    void testCancelAutoBidOutbidFlow_EC201_VulnerabilityFixed() throws Exception {
        String runId = "EC201-" + System.currentTimeMillis();
        
        User seller = new User();
        seller.setId("SELLER-" + runId);
        seller.setUserName("seller-" + runId);

        User userA = new User();
        userA.setId("USER-A-" + runId);
        userA.setUserName("user-a-" + runId);

        User userB = new User();
        userB.setId("USER-B-" + runId);
        userB.setUserName("user-b-" + runId);

        Item item = new TangibleItem("ITEM-" + runId, "EC201 Item", "", 100_000L);

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            
            try (var ps = conn.prepareStatement("INSERT INTO users (id, username, password, name, role, is_good) VALUES (?, ?, 'pw', ?, 'USER', 1)")) {
                ps.setString(1, seller.getId());
                ps.setString(2, seller.getUserName());
                ps.setString(3, "Seller");
                ps.executeUpdate();
                
                ps.setString(1, userA.getId());
                ps.setString(2, userA.getUserName());
                ps.setString(3, "User A");
                ps.executeUpdate();
                
                ps.setString(1, userB.getId());
                ps.setString(2, userB.getUserName());
                ps.setString(3, "User B");
                ps.executeUpdate();
            }
            
            walletDAO.createWallet(conn, userA.getId());
            walletDAO.createWallet(conn, userB.getId());
            
            walletDAO.updateBalance(conn, userA.getId(), 1_000_000L);
            walletDAO.updateBalance(conn, userB.getId(), 1_000_000L);
            
            conn.commit();
        }

        LocalDateTime now = LocalDateTime.now().minusSeconds(5);
        Auction auction = new Auction("AUC-" + runId, item, seller, 10_000L, now, now.plusMinutes(10), 10);
        auction.setStatus(Auction.STATUS_RUNNING);
        assertTrue(auctionDAO.addAuction(auction), "Should create test auction");

        // User A registers Auto-Bid
        boolean autoBidOk = controller.setupAutoBid(userA, auction, 500_000L, 20_000L).get();
        assertTrue(autoBidOk, "Should set up auto-bid for User A");

        // Verify balance lock: 500k locked
        {
            var data = walletDAO.getWalletData(userA.getId());
            assertEquals(500_000L, data.get("balance"));
            assertEquals(500_000L, data.get("lockedBalance"));
        }

        // Trigger bot engine scan to bid on behalf of User A
        service.AutoBidEngine.triggerBotScan(auction);
        
        // Sleep briefly for async Virtual Thread executor to place bid
        Thread.sleep(300);

        // Verify User A is winning
        synchronized (server.ServerExtension.AuctionManager.getLockForAuction(auction.getId())) {
            assertEquals(userA.getId(), auction.getWinningBidder().getId());
            assertEquals(100_000L, auction.getCurrentPrice());
            assertEquals(500_000L, auction.getHighestMaxBid());
        }

        // User A cancels their auto-bid while leading
        boolean cancelOk = controller.cancelAutoBid(userA, auction).get();
        assertTrue(cancelOk, "Should cancel User A's auto-bid");

        // Verify collateral is retained (still leading winner)
        {
            var data = walletDAO.getWalletData(userA.getId());
            assertEquals(500_000L, data.get("balance"));
            assertEquals(500_000L, data.get("lockedBalance"));
        }

        // User B outbids User A with a manual bid of 300,000 VND
        boolean bidBOk = controller.placeManualBid(userB, auction, 300_000L).get();
        assertTrue(bidBOk, "User B should successfully bid 300,000 VND");

        // Verify User B is the new winner
        synchronized (server.ServerExtension.AuctionManager.getLockForAuction(auction.getId())) {
            assertEquals(userB.getId(), auction.getWinningBidder().getId());
            assertEquals(300_000L, auction.getCurrentPrice());
            assertEquals(300_000L, auction.getHighestMaxBid());
        }

        // CRITICAL VULNERABILITY ASSERTION:
        // Since User A's Auto-bid was cancelled, outbidding User A MUST trigger
        // a full release of User A's 500,000 VND locked collateral immediately!
        {
            var data = walletDAO.getWalletData(userA.getId());
            assertEquals(1_000_000L, data.get("balance"), "User A's balance must be fully refunded to 1,000,000 VND!");
            assertEquals(0L, data.get("lockedBalance"), "User A's locked balance must be completely un-frozen!");
        }
    }

    /**
     * Test Case for EC-205: Optimistic Concurrency Control (OCC) / Race Conditions.
     * Simulates two threads sending the exact same bid of 150,000 VND concurrently.
     * Asserts that exactly one bid succeeds and the other gets blocked/rejected by SQLite's CAS mechanism.
     */
    @Test
    void testConcurrentPlaceBid_EC205_RaceCondition() throws Exception {
        String runId = "EC205-" + System.currentTimeMillis();
        
        User seller = new User();
        seller.setId("SELLER-" + runId);
        seller.setUserName("seller-" + runId);

        User userA = new User();
        userA.setId("USER-A-" + runId);
        userA.setUserName("user-a-" + runId);

        User userB = new User();
        userB.setId("USER-B-" + runId);
        userB.setUserName("user-b-" + runId);

        Item item = new TangibleItem("ITEM-" + runId, "EC205 Item", "", 100_000L);

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try (var ps = conn.prepareStatement("INSERT INTO users (id, username, password, name, role, is_good) VALUES (?, ?, 'pw', ?, 'USER', 1)")) {
                ps.setString(1, seller.getId());
                ps.setString(2, seller.getUserName());
                ps.setString(3, "Seller");
                ps.executeUpdate();
                
                ps.setString(1, userA.getId());
                ps.setString(2, userA.getUserName());
                ps.setString(3, "User A");
                ps.executeUpdate();
                
                ps.setString(1, userB.getId());
                ps.setString(2, userB.getUserName());
                ps.setString(3, "User B");
                ps.executeUpdate();
            }
            walletDAO.createWallet(conn, userA.getId());
            walletDAO.createWallet(conn, userB.getId());
            walletDAO.updateBalance(conn, userA.getId(), 500_000L);
            walletDAO.updateBalance(conn, userB.getId(), 500_000L);
            conn.commit();
        }

        LocalDateTime now = LocalDateTime.now().minusSeconds(5);
        Auction auction = new Auction("AUC-" + runId, item, seller, 10_000L, now, now.plusMinutes(10), 10);
        auction.setStatus(Auction.STATUS_RUNNING);
        assertTrue(auctionDAO.addAuction(auction));

        // Submit two identical bids concurrently
        var futureA = controller.placeManualBid(userA, auction, 150_000L);
        var futureB = controller.placeManualBid(userB, auction, 150_000L);

        boolean resultA = futureA.get();
        boolean resultB = futureB.get();

        // One must succeed, one must fail
        assertTrue(resultA || resultB, "At least one concurrent bid must succeed");
        assertNotEquals(resultA, resultB, "Only exactly one bid can succeed; the other must fail due to CAS update conflict");

        // Verify balance locks
        {
            var dataA = walletDAO.getWalletData(userA.getId());
            var dataB = walletDAO.getWalletData(userB.getId());

            if (resultA) {
                assertEquals(150_000L, dataA.get("lockedBalance"), "User A should have funds locked");
                assertEquals(0L, dataB.get("lockedBalance"), "User B should not have funds locked");
            } else {
                assertEquals(0L, dataA.get("lockedBalance"), "User A should not have funds locked");
                assertEquals(150_000L, dataB.get("lockedBalance"), "User B should have funds locked");
            }
        }
    }
}
