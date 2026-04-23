package controller;

import database.DatabaseManager;
import model.Auction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static utils.ConsoleColors.*;

public class AuctionMonitor {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private List<Auction> allAuctions;

    public AuctionMonitor(List<Auction> allAuctions) {
        this.allAuctions = allAuctions;
    }

    public void startMonitoring() {
        System.out.println("[Monitor]:" + GREEN + " The automatic auction monitoring system has been launched" + RESET);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 1. Snapshot an toàn
                List<Auction> safeSnapshot;
                synchronized (allAuctions) {
                    safeSnapshot = new ArrayList<>(allAuctions);
                }

                // 2. Duyệt snapshot
                for (Auction auction : safeSnapshot) {
                    synchronized (auction) {
                        if (auction.getStatus().equals(Auction.STATUS_RUNNING)) {
                            // Nhận về trạng thái mới (FINISHED hoặc CANCELED)
                            String newStatus = auction.closeAuctionIfTimeIsUp();

                            // 3. Nếu thực sự có thay đổi, lưu ngay xuống Database
                            if (newStatus != null) {
                                String sql = "UPDATE auctions SET status = ? WHERE id = ?";
                                try (Connection conn = DatabaseManager.getConnection();
                                     PreparedStatement pstmt = conn.prepareStatement(sql)) {
                                    pstmt.setString(1, newStatus);
                                    pstmt.setString(2, auction.getId());
                                    pstmt.executeUpdate();
                                } catch (SQLException e) {
                                    System.out.println("[Error]: DB Sync failed for monitor: " + RED + e.getMessage() + RESET);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[Error]: Error during the bidding scan process: " + RED + e.getMessage() + RESET);
                e.printStackTrace();
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    public void stopMonitoring() {
        scheduler.shutdown();
        System.out.println(YELLOW + "[Monitor]: The auction monitoring system has been turned off" + RESET);
    }
}