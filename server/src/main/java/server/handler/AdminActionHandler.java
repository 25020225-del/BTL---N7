package server.handler;

import database.DatabaseManager;
import database.TransactionManager;
import model.User;
import network.NetworkMessage;
import server.ClientHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.Callable;

import static utils.ConsoleColors.*;

public class AdminActionHandler implements CommandHandler {

    @Override
    public void handle(NetworkMessage message, ClientHandler client) {
        String command = message.getCommand();
        User admin = client.getUser();

        // Security check: Only admins can use this command
        if (admin == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
            client.sendResponse("ERROR", "You do not have permission to perform this command.");
            return;
        }

        String auctionId = (String) message.getData();

        if ("APPROVE_AUCTION".equals(command)) {
            processApproval(auctionId, "OPEN", client);
        } else if ("REJECT_AUCTION".equals(command)) {
            processApproval(auctionId, "CANCELED", client);
        }
    }

    private void processApproval(String auctionId, String newStatus, ClientHandler client) {
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