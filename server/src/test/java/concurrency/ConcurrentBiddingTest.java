package concurrency;

import controller.ServerBidderController;
import database.DatabaseManager;
import database.dao.AuctionDAO;
import database.dao.BidDAO;
import database.dao.WalletDAO;
import model.auction.Auction;
import model.item.Item;
import model.user.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentBiddingTest {

    @BeforeAll
    static void initDb() {
        DatabaseManager.initializeDatabase();
    }

    @AfterAll
    static void shutdownPool() {
        // no-op: DatabaseManager uses a static pool for app lifecycle
    }

    @Test
    void optimisticLocking_allowsExactlyOneWinnerUpdatePerState() throws Exception {
        WalletDAO walletDAO = new WalletDAO();
        AuctionDAO auctionDAO = new AuctionDAO();
        BidDAO bidDAO = new BidDAO();
        ServerBidderController controller = new ServerBidderController(bidDAO);

        String runId = "T1-" + System.currentTimeMillis();

        // --- Arrange: create seller + 10 bidders with sufficient balance ---
        User seller = new User();
        seller.setId("SELLER-" + runId);
        seller.setUserName("seller-" + runId);

        Item item = new Item();
        item.setId("ITEM-" + runId);
        item.setItemName("Test Item");
        item.setStartingPrice(1000.0);

        LocalDateTime now = LocalDateTime.now().minusSeconds(5);
        Auction auction = new Auction("AUC-" + runId, item, seller, 50.0, now, now.plusMinutes(10));
        auction.setStatus(Auction.STATUS_RUNNING);

        List<User> bidders = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            User u = new User();
            u.setId("BIDDER-" + runId + "-" + i);
            u.setUserName("bidder-" + runId + "-" + i);
            u.setName("Bidder " + i);
            bidders.add(u);
        }

        // Seed users + wallets directly (users table has FK dependencies)
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            // Seller
            try (var ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO users (id, username, password, name, role, is_good, is_totp_enabled, is_blocked) " +
                            "VALUES (?, ?, 'x', ?, 'SELLER', 1, 0, 0)")) {
                ps.setString(1, seller.getId());
                ps.setString(2, seller.getUserName());
                ps.setString(3, "Seller");
                ps.executeUpdate();
            }

            for (User u : bidders) {
                try (var ps = conn.prepareStatement(
                        "INSERT OR IGNORE INTO users (id, username, password, name, role, is_good, is_totp_enabled, is_blocked) " +
                                "VALUES (?, ?, 'x', ?, 'USER', 0, 0, 0)")) {
                    ps.setString(1, u.getId());
                    ps.setString(2, u.getUserName());
                    ps.setString(3, u.getName());
                    ps.executeUpdate();
                }

                // Create wallet row if missing; then set balance high enough
                try {
                    walletDAO.createWallet(conn, u.getId());
                } catch (SQLException ignored) {}
                walletDAO.updateBalance(conn, u.getId(), 100_000.0);
            }
            conn.commit();
        }

        // Ensure auction exists in DB (after users exist)
        assertTrue(auctionDAO.addAuction(auction), "Test requires inserting a fresh auction row");

        // --- Act: 10 concurrent bids all trying to set the same max bid ---
        ExecutorService exec = Executors.newFixedThreadPool(10);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (User bidder : bidders) {
            futures.add(exec.submit(() -> {
                startGate.await(2, TimeUnit.SECONDS);
                return controller.placeBidOnAuction(bidder, auction, 1500.0, false).get(10, TimeUnit.SECONDS);
            }));
        }

        startGate.countDown();

        int successCount = 0;
        for (Future<Boolean> f : futures) {
            if (Boolean.TRUE.equals(f.get(12, TimeUnit.SECONDS))) successCount++;
        }
        exec.shutdownNow();

        // --- Assert: with optimistic locking, only one transaction should win from the same initial state ---
        assertEquals(1, successCount, "Exactly one bid should commit; the rest should fail optimistic locking.");
    }
}

