package server.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import database.DatabaseManager;
import database.TransactionManager;
import database.dao.AuctionDAO;
import model.auction.Auction;
import model.user.User;
import network.NetworkMessage;
import server.ClientHandler;
import server.ServerExtension.AuctionManager;

import java.time.LocalDateTime;
import java.util.concurrent.Callable;

import static utils.ConsoleColors.*;

/**
 * Handles administrative commands sent by clients.
 * This handler manages the approval and rejection of auction requests,
 * ensuring that only users with the "ADMIN" role can execute these operations.
 */
public class AdminActionHandler implements CommandHandler {
    private static final Logger log = LoggerFactory.getLogger(AdminActionHandler.class);
    private final AuctionDAO auctionDAO;

    /**
     * Constructs the handler with necessary DAOs via Dependency Injection.
     *
     * @param auctionDAO The DAO for auction management.
     */
    public AdminActionHandler(AuctionDAO auctionDAO) {
        this.auctionDAO = auctionDAO;
    }

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
            try {
                LocalDateTime[] times = auctionDAO.getAuctionTimes(auctionId);
                if (times == null) return false;

                LocalDateTime oldStart = times[0];
                LocalDateTime oldEnd = times[1];
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime newStart = oldStart;
                LocalDateTime newEnd = oldEnd;

                if (newStatus.equals("OPEN")) {
                    if (oldStart == null || oldStart.isBefore(now) || oldStart.isEqual(now)) {
                        long duration = 60; // Default fallback duration
                        if (oldStart != null && oldEnd != null) {
                            duration = java.time.Duration.between(oldStart, oldEnd).toMinutes();
                        }
                        newStart = now;
                        newEnd = now.plusMinutes(duration);
                        System.out.println("[System]: Admin approved late or immediate start. Recalculated new start time to NOW.");
                    } else {
                        System.out.println("[System]: Admin approved early for a future scheduled auction. Kept original times.");
                    }
                }

                // Default values if recalculation results in null (unlikely but safe)
                if (newStart == null) newStart = now;
                if (newEnd == null) newEnd = now.plusMinutes(60);

                return auctionDAO.updateApprovalStatus(auctionId, newStatus, newStart, newEnd);
            } catch (Exception e) {
                log.warn("Updating approval status failed: {}", e.getMessage());
                return false;
            }
        };

        TransactionManager.submitTask(updateTask).thenAccept(success -> {
            if (success) {
                String msg = newStatus.equals("OPEN") ? "Auction approved" : "Auction declined";
                client.sendResponse("ADMIN_ACTION_SUCCESS", msg);
                log.info("{} has changed the status of {} to {}", client.getUser().getUserName(), auctionId, newStatus);

                // If approved, load the auction into RAM for monitoring
                if (newStatus.equals("OPEN")) {
                    try {
                        Auction auction = auctionDAO.getAuctionById(auctionId);
                        if (auction != null) {
                            AuctionManager.addAuctionToMonitor(auction);
                            System.out.println("[System]: Auction " + auctionId + " added to RAM monitor after Admin approval.");
                        }
                    } catch (Exception e) {
                        log.error("Failed to load approved auction into RAM: {}", e.getMessage());
                    }
                }
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