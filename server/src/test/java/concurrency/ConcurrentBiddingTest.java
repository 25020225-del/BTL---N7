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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentBiddingTest {

    @BeforeAll
    static void initDb() {
        DatabaseManager.initializeDatabase();
    }

    @AfterAll
    static void shutdownPool() {
        //DatabaseManager.closePool();
    }

    @Test
    void optimisticLocking_DataIntegrity_Verification() throws Exception {
        WalletDAO walletDAO = new WalletDAO();
        AuctionDAO auctionDAO = new AuctionDAO();
        BidDAO bidDAO = new BidDAO();
        ServerBidderController controller = new ServerBidderController(bidDAO);

        String runId = "T1-" + System.currentTimeMillis();
        long initialBalance = 100_000L;
        long bidAmount = 1500L;

        // --- Arrange: Tạo Seller và 10 Bidders ---
        User seller = new User();
        seller.setId("SELLER-" + runId);
        seller.setUserName("seller-" + runId);

        Item item = new TangibleItem("ITEM-" + runId, "Test Item", "", 1000L);

        LocalDateTime now = LocalDateTime.now().minusSeconds(5);
        Auction auction = new Auction("AUC-" + runId, item, seller, 50L, now, now.plusMinutes(10));
        auction.setStatus(Auction.STATUS_RUNNING);

        List<User> bidders = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            User u = new User();
            u.setId("BIDDER-" + runId + "-" + i);
            u.setUserName("bidder-" + runId + "-" + i);
            u.setName("Bidder " + i);
            bidders.add(u);
        }

        // Seed DB trực tiếp
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try (var ps = conn.prepareStatement("INSERT OR IGNORE INTO users (id, username, password, name, role, is_good) VALUES (?, ?, 'x', ?, 'SELLER', 1)")) {
                ps.setString(1, seller.getId());
                ps.setString(2, seller.getUserName());
                ps.setString(3, "Seller");
                ps.executeUpdate();
            }

            for (User u : bidders) {
                try (var ps = conn.prepareStatement("INSERT OR IGNORE INTO users (id, username, password, name, role, is_good) VALUES (?, ?, 'x', ?, 'USER', 0)")) {
                    ps.setString(1, u.getId());
                    ps.setString(2, u.getUserName());
                    ps.setString(3, u.getName());
                    ps.executeUpdate();
                }
                try {
                    walletDAO.createWallet(conn, u.getId());
                } catch (SQLException ignored) {
                }
                walletDAO.updateBalance(conn, u.getId(), initialBalance);
            }
            conn.commit();
        }

        assertTrue(auctionDAO.addAuction(auction), "Phải tạo được phiên đấu giá ảo");

        // --- Act: Ép 10 Thread bắn chung 1 mili-giây ---
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(10);
        AtomicInteger successCount = new AtomicInteger(0);

        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final User bidder = bidders.get(i);
            Thread worker = new Thread(() -> {
                try {
                    startLatch.await(); // Đợi hiệu lệnh
                    boolean ok = Boolean.TRUE.equals(controller.placeBidOnAuction(bidder, auction, bidAmount, false).get(30, TimeUnit.SECONDS));
                    if (ok) successCount.incrementAndGet();
                } catch (Exception e) {
                    throw new AssertionError("Worker thread failed", e);
                } finally {
                    doneLatch.countDown();
                }
            });
            workers.add(worker);
            worker.start();
        }

        startLatch.countDown(); // Khai hỏa
        assertTrue(doneLatch.await(60, TimeUnit.SECONDS), "Deadlock xảy ra: Không phải tất cả 10 thread đều hoàn thành.");

        // Cơ bản: Phải có ít nhất 1 request lọt qua khe cửa hẹp của Optimistic Lock
        assertTrue(successCount.get() >= 1 && successCount.get() <= bidders.size(), "Phải có ít nhất 1 giao dịch thành công.");

        // --- ASSERT: DATA INTEGRITY (QUAN TRỌNG NHẤT) ---
        try (Connection conn = DatabaseManager.getConnection()) {

            // 1. Xác định Vua trò chơi
            String winnerId = null;
            long highestMaxBid = 0;
            try (var ps = conn.prepareStatement("SELECT winning_bidder_id, highest_max_bid FROM auctions WHERE id = ?")) {
                ps.setString(1, auction.getId());
                var rs = ps.executeQuery();
                if (rs.next()) {
                    winnerId = rs.getString("winning_bidder_id");
                    highestMaxBid = rs.getLong("highest_max_bid");
                }
            }
            assertNotNull(winnerId, "Database: Bắt buộc phải chốt được 1 người chiến thắng cuối cùng.");

            // 2. Tra soát đối soát tài chính toàn bộ 10 Bidders
            for (User u : bidders) {
                try (var ps = conn.prepareStatement("SELECT balance, locked_balance FROM wallets WHERE user_id = ?")) {
                    ps.setString(1, u.getId());
                    var rs = ps.executeQuery();
                    if (rs.next()) {
                        long balance = rs.getLong("balance");
                        long locked = rs.getLong("locked_balance");

                        if (u.getId().equals(winnerId)) {
                            assertEquals(highestMaxBid, locked, "Người thắng: Số tiền giam phải bằng đúng Max Bid cuối cùng.");
                            assertEquals(initialBalance - highestMaxBid, balance, "Người thắng: Số dư khả dụng bị lệch so với số tiền bị giam.");
                        } else {
                            assertEquals(0L, locked, "Người thua: Tiền giam phải được trả về 0.");
                            assertEquals(initialBalance, balance, "Người thua: Phải được hoàn tiền 100% không thiếu một đồng.");
                        }
                    }
                }
            }

            // 3. Đối soát lịch sử giao dịch (Chống tạo khống Transaction)
            try (var ps = conn.prepareStatement("SELECT COUNT(*) FROM bid_transactions WHERE auction_id = ?")) {
                ps.setString(1, auction.getId());
                var rs = ps.executeQuery();
                if (rs.next()) {
                    int txnCount = rs.getInt(1);
                    assertEquals(successCount.get(), txnCount, "Số lượng bản ghi trong bid_transactions phải khớp chính xác với số lần đặt giá báo SUCCESS.");
                }
            }
        }
    }
}