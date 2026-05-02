package server.handler;

import database.DatabaseManager;
import database.TransactionManager;
import model.user.User;
import network.NetworkMessage;
import server.ClientHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
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
     * This method wraps the update logic into a {@link Callable} and submits it to
     * the {@link TransactionManager} to maintain database integrity and avoid
     * blocking the main network thread.
     *
     * @param auctionId The unique identifier of the auction to update.
     * @param newStatus The target status (e.g., "OPEN" for approval, "CANCELED" for rejection).
     * @param client    The client handler used to send success or error feedback.
     */
    private void processApproval(String auctionId, String newStatus, ClientHandler client) {
        // Prepare the database update task for the transaction queue
        Callable<Boolean> updateTask = () -> {
            String sql = "UPDATE auctions SET status = ? WHERE id = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, newStatus);
                pstmt.setString(2, auctionId);
                int rows = pstmt.executeUpdate();
                return rows > 0;
            } catch (Exception e) {
                System.out.println("[System](AdminActionHandler): Updating approval status failed: " + RED + e.getMessage() + RESET);
                return false;
            }
        };

        try {
            // Submit the task to TransactionManager and wait for the result
            // This ensures SQLite write operations are serialized correctly
            boolean success = TransactionManager.submitTask(updateTask).get();
            if (success) {
                String msg = newStatus.equals("OPEN") ? "Auction approved" : "Auction declined";
                client.sendResponse("ADMIN_ACTION_SUCCESS", msg);
                System.out.println("[System]: Admin \"" + YELLOW + client.getUser().getName() + RESET + "\" has changed the status of " + auctionId + " to " + newStatus);
            } else {
                client.sendResponse("ERROR", "Cannot find this auction in database.");
            }
        } catch (Exception e) {
            client.sendResponse("ERROR", "Server error.");
        }
    }
}