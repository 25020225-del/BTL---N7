package controller;

import database.DatabaseManager;
<<<<<<< HEAD
import model.Auction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
=======
import database.TransactionManager;
import model.auction.Auction;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
>>>>>>> df73b5cfd21e32839620dec3b4e4f4bde75eecf1
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static utils.ConsoleColors.*;

/**
 * A background daemon service that continuously monitors active auctions.
 * It manages real-time expiration in RAM and routinely sweeps the database
 * to clean up any "orphaned" or "ghost" auctions left over from previous server sessions.
 */
public class AuctionMonitor {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private List<Auction> allAuctions;

    /**
     * Constructs the monitor with a reference to the global active auction list in RAM.
     *
     * @param allAuctions The shared list of currently monitored auctions.
     */
    public AuctionMonitor(List<Auction> allAuctions) {
        this.allAuctions = allAuctions;
    }

    /**
     * Starts the scheduled background task.
     * The task runs periodically to check if any auction has exceeded its end time,
     * handling both volatile RAM instances and persistent Database records.
     */
    public void startMonitoring() {
        System.out.println("[System]:" + GREEN + " Auction monitor has been launched." + RESET);

        scheduler.scheduleAtFixedRate(() -> {
            try {
<<<<<<< HEAD
                List<Auction> safeSnapshot;
                synchronized (allAuctions) {
                    safeSnapshot = new ArrayList<>(allAuctions);
                }

                for (Auction auction : safeSnapshot) {
                    synchronized (auction) {
                        if (auction.getStatus().equals(Auction.STATUS_RUNNING)) {
                            String newStatus = auction.closeAuctionIfTimeIsUp();

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
=======
                // 1. Process active auctions currently held in Server RAM
                processRamAuctions();

                // 2. Sweep the database for any orphaned/ghost auctions (e.g., from prior server crashes)
                sweepDatabaseForOrphans();

>>>>>>> df73b5cfd21e32839620dec3b4e4f4bde75eecf1
            } catch (Exception e) {
                System.out.println("[System](AuctionMonitor): Error occurred during bidding scan process: " + RED + e.getMessage() + RESET);
                e.printStackTrace();
            }
        }, 0, 10, TimeUnit.SECONDS); // Scans every 10 seconds
    }

    /**
     * Iterates through the in-memory auction list, finalizing those whose time has expired.
     */
    private void processRamAuctions() {
        for (Auction auction : allAuctions) {

            // Check both RUNNING and OPEN statuses to handle auctions with zero bids
            if (auction.getStatus().equals(Auction.STATUS_RUNNING) || auction.getStatus().equals(Auction.STATUS_OPEN)) {
                auction.closeAuctionIfTimeIsUp();
            }

            String status = auction.getStatus();
            if (status.equals(Auction.STATUS_FINISHED) ||
                    status.equals(Auction.STATUS_CANCELED) ||
                    status.equals(Auction.STATUS_DELETED)) {

                // Remove from Server RAM to prevent memory leaks
                allAuctions.remove(auction);
                AuctionManager.removeAuctionLock(auction.getId());

                // Persist the closed status to the SQLite Database asynchronously
                Callable<Boolean> dbUpdateTask = () -> {
                    String sql = "UPDATE auctions SET status = ? WHERE id = ?";
                    try (Connection conn = DatabaseManager.getConnection();
                         PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, status);
                        pstmt.setString(2, auction.getId());
                        pstmt.executeUpdate();
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                };
                TransactionManager.submitTask(dbUpdateTask);

                // Broadcast removal command to all connected clients
                ClientManager.broadcast("REMOVE_AUCTION", auction.getId(), null);

                System.out.println("[System]: " + BLUE + "Removed auction " + YELLOW + auction.getId() + RESET + " from RAM and updated DB to " + status);
            }
        }
    }

    /**
     * Scans the database directly to find and close any auctions that expired
     * but were not loaded into RAM (e.g., created before a recent server restart).
     */
    private void sweepDatabaseForOrphans() {
        Callable<Boolean> dbSweepTask = () -> {
            String selectSql = "SELECT id, end_time, current_price, starting_price FROM auctions WHERE status IN ('OPEN', 'RUNNING')";
            String updateSql = "UPDATE auctions SET status = ? WHERE id = ?";

            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement selectStmt = conn.prepareStatement(selectSql);
                 ResultSet rs = selectStmt.executeQuery()) {

                while (rs.next()) {
                    String id = rs.getString("id");
                    String endTimeStr = rs.getString("end_time");
                    double currentPrice = rs.getDouble("current_price");
                    double startPrice = rs.getDouble("starting_price");

                    LocalDateTime endTime = LocalDateTime.parse(endTimeStr);

                    // Verify if the database auction's deadline has passed
                    if (LocalDateTime.now().isAfter(endTime)) {

                        // Determine if it was sold or canceled based on price progression
                        String newStatus = (currentPrice > startPrice) ? "FINISHED" : "CANCELED";

                        // Execute atomic update directly to the Database
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                            updateStmt.setString(1, newStatus);
                            updateStmt.setString(2, id);
                            updateStmt.executeUpdate();
                        }

                        // Force clients to remove the ghost item from their UI
                        ClientManager.broadcast("REMOVE_AUCTION", id, null);
                        System.out.println("[System]: " + BLUE + "Swept and closed orphaned database auction: " + YELLOW + id + RESET + " -> " + newStatus);
                    }
                }
                return true;
            } catch (Exception e) {
                System.out.println("[Database]: Orphan sweep error: " + RED + e.getMessage() + RESET);
                return false;
            }
        };

        // Push the sweeping task to the database thread queue
        TransactionManager.submitTask(dbSweepTask);
    }

    /**
     * Gracefully shuts down the monitoring daemon service.
     */
    public void stopMonitoring() {
        scheduler.shutdown();
        System.out.println("[System]: " + YELLOW + " Auction monitor has been shutdown." + RESET);
    }
}