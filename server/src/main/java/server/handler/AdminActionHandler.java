package server.handler;

import database.DatabaseManager;
import database.TransactionManager;
import model.user.User;
import network.NetworkMessage;
import server.ClientHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;

import static utils.ConsoleColors.*;

/**
 * Handles administrative commands sent by clients.
 * This handler manages the approval and rejection of auction requests,
 * ensuring that only users with the "ADMIN" role can execute these operations.
 */
public class AdminActionHandler implements CommandHandler {

    /**
     * Entry point for handling administrative network messages.
     * Performs a security check to verify the user's role before dispatching the command
     * to the appropriate processing logic.
     *
     * @param message The network message containing the command and the target auction ID.
     * @param client  The handler for the specific client connection.
     */
    @Override
    public void handle(NetworkMessage message, ClientHandler client) {
        String command = message.getCommand();
        User admin = client.getUser();

        // Security check: Only admins can use this command
        if (admin == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
            client.sendResponse("ERROR", "You do not have permission to perform this command.");
            return;
        }

        // The data payload is expected to be the unique ID of the auction
        String auctionId = (String) message.getData();

        if ("APPROVE_AUCTION".equals(command)) {
            processApproval(auctionId, "OPEN", client);
        } else if ("REJECT_AUCTION".equals(command)) {
            processApproval(auctionId, "CANCELED", client);
        }
    }

    /**
     * Processes the status change of an auction in the database.
     * Calculates dynamic start and end times upon approval to ensure auctions
     * configured to "start immediately" begin exactly when the Admin approves them.
     *
     * @param auctionId The unique identifier of the auction to update.
     * @param newStatus The target status (e.g., "OPEN" for approval, "CANCELED" for rejection).
     * @param client    The client handler used to send success or error feedback.
     */
    private void processApproval(String auctionId, String newStatus, ClientHandler client) {
        Callable<Boolean> updateTask = () -> {
            String selectSql = "SELECT start_time, end_time FROM auctions WHERE id = ?";
            String updateSql = "UPDATE auctions SET status = ?, start_time = ?, end_time = ? WHERE id = ?";

            try (Connection conn = DatabaseManager.getConnection()) {
                LocalDateTime oldStart = null;
                LocalDateTime oldEnd = null;

                // Fetch the original times saved during creation
                try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                    pstmt.setString(1, auctionId);
                    java.sql.ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        String startTimeStr = rs.getString("start_time");
                        String endTimeStr = rs.getString("end_time");
                        
                        // Safeguard against null or empty dates in the database
                        if (startTimeStr != null && !startTimeStr.trim().isEmpty()) {
                            oldStart = LocalDateTime.parse(startTimeStr);
                        }
                        if (endTimeStr != null && !endTimeStr.trim().isEmpty()) {
                            oldEnd = LocalDateTime.parse(endTimeStr);
                        }
                    } else {
                        return false;
                    }
                }

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime newStart = oldStart;
                LocalDateTime newEnd = oldEnd;

                if (newStatus.equals("OPEN")) {
                    // DYNAMIC RECALCULATION LOGIC:
                    // 1. oldStart is null/empty (Fallback safeguard)
                    // 2. oldStart has already passed (Admin approved late)
                    // 3. oldStart was 'now' at creation time (so it is definitely in the past compared to server's 'now' at approval time)
                    if (oldStart == null || oldStart.isBefore(now) || oldStart.isEqual(now)) {
                        
                        long duration = 60; // Default fallback duration
                        if (oldStart != null && oldEnd != null) {
                            duration = java.time.Duration.between(oldStart, oldEnd).toMinutes();
                        }
                        
                        newStart = now;
                        newEnd = now.plusMinutes(duration);
                        System.out.println("[System]: Admin approved late or immediate start. Recalculated new start time to NOW.");
                    } else {
                        // Admin is approving EARLY for a future scheduled auction.
                        // MUST KEEP ORIGINAL oldStart and oldEnd!
                        newStart = oldStart;
                        newEnd = oldEnd;
                        System.out.println("[System]: Admin approved early for a future scheduled auction. Kept original times.");
                    }
                }

                // Update the database with the adjusted times
                try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                    pstmt.setString(1, newStatus);
                    pstmt.setString(2, newStart != null ? newStart.toString() : now.toString());
                    pstmt.setString(3, newEnd != null ? newEnd.toString() : now.plusMinutes(60).toString());
                    pstmt.setString(4, auctionId);
                    int rows = pstmt.executeUpdate();
                    return rows > 0;
                }
            } catch (Exception e) {
                System.out.println("[System](AdminActionHandler): Updating approval status failed: " + RED + e.getMessage() + RESET);
                return false;
            }
        };

        TransactionManager.submitTask(updateTask).thenAccept(success -> {
            if (success) {
                String msg = newStatus.equals("OPEN") ? "Auction approved" : "Auction declined";
                client.sendResponse("ADMIN_ACTION_SUCCESS", msg);
                System.out.println("[System]: Admin \"" + YELLOW + client.getUser().getName() + RESET + "\" has changed the status of " + auctionId + " to " + newStatus);
            } else {
                client.sendResponse("ERROR", "Cannot find this auction in database.");
            }
        }).exceptionally(ex -> {
            // Error handling fallback
            client.sendResponse("ERROR", "Server error.");
            return null;
        });
    }
}