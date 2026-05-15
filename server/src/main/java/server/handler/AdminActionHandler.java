package server.handler;

import database.TransactionManager;
import database.dao.AuctionDAO;
import model.user.User;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ClientHandler;

/**
 * Handles administrative commands sent by clients.
 * This handler acts as a network router, ensuring that only users with the "ADMIN" role
 * can execute operations, and delegates business logic to the ServerAdminController.
 */
public class AdminActionHandler implements CommandHandler {
    private static final Logger log = LoggerFactory.getLogger(AdminActionHandler.class);
    private final database.dao.UserDAO userDAO;
    private final controller.ServerAdminController adminCtrl;

    public AdminActionHandler(AuctionDAO auctionDAO, database.dao.UserDAO userDAO, controller.ServerAdminController adminCtrl) {
        this.userDAO = userDAO;
        this.adminCtrl = adminCtrl;
    }

    @Override
    public void handle(NetworkMessage message, ClientHandler client) {
        String command = message.getCommand();
        User adminUser = client.getUser();

        // Security check: Only admins can use this command
        if (adminUser == null || !adminUser.getRole().equalsIgnoreCase("ADMIN")) {
            client.sendResponse("ERROR", "You do not have permission to perform this command.");
            return;
        }

        model.user.Admin admin = new model.user.Admin(adminUser);

        if ("FETCH_USERS".equals(command)) {
            handleFetchUsers(client);
            return;
        } else if ("BLOCK_USER".equals(command)) {
            handleUserBlock(admin, message.getData().toString(), true, client);
            return;
        } else if ("UNBLOCK_USER".equals(command)) {
            handleUserBlock(admin, message.getData().toString(), false, client);
            return;
        }

        String auctionId = (String) message.getData();

        if ("APPROVE_AUCTION".equals(command)) {
            TransactionManager.submitTask(() -> adminCtrl.approveAuction(admin, auctionId))
                    .thenAccept(success -> {
                        if (success) {
                            client.sendResponse("ADMIN_ACTION_SUCCESS", "Auction approved");
                        } else {
                            client.sendResponse("ERROR", "Cannot find this auction in database or approval failed.");
                        }
                    }).exceptionally(ex -> {
                        log.error("Async approval failed: {}", ex.getMessage());
                        client.sendResponse("ERROR", "Server error.");
                        return null;
                    });
        } else if ("REJECT_AUCTION".equals(command)) {
            TransactionManager.submitTask(() -> adminCtrl.rejectAuction(admin, auctionId))
                    .thenAccept(success -> {
                        if (success) {
                            client.sendResponse("ADMIN_ACTION_SUCCESS", "Auction declined");
                        } else {
                            client.sendResponse("ERROR", "Cannot find this auction in database or rejection failed.");
                        }
                    }).exceptionally(ex -> {
                        log.error("Async rejection failed: {}", ex.getMessage());
                        client.sendResponse("ERROR", "Server error.");
                        return null;
                    });
        }
    }

    private void handleFetchUsers(ClientHandler client) {
        try {
            java.util.List<java.util.Map<String, Object>> users = userDAO.getAllUsers();
            client.sendResponse("FETCH_USERS_SUCCESS", users);
        } catch (java.sql.SQLException e) {
            client.sendResponse("ERROR", "Failed to fetch users: " + e.getMessage());
        }
    }

    private void handleUserBlock(model.user.Admin admin, String userId, boolean block, ClientHandler client) {
        boolean success = block ?
                adminCtrl.blockUser(admin, userId) : adminCtrl.unblockUser(admin, userId);
        if (success) {
            String action = block ? "blocked" : "unblocked";
            client.sendResponse("ADMIN_ACTION_SUCCESS", "User " + userId + " has been " + action);
        } else {
            client.sendResponse("ERROR", "Failed to update user status.");
        }
    }
}