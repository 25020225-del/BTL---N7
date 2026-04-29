package server.ClientHandlerExtension;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import network.NetworkMessage;
import server.ClientHandler;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;

import static utils.ConsoleColors.*;

public class AuctionActionHandler implements CommandHandler {
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public void handle(NetworkMessage message, ClientHandler client) {
        if ("CREATE_AUCTION".equals(message.getCommand())) {
            processCreateAuction(message.getData(), client);
        }
    }

    private void processCreateAuction(Object data, ClientHandler client) {
        try {
            // Get user identification from login session
            model.User authenticatedUser = client.getUser();

            // Security check: not login or not seller = block
            if (authenticatedUser == null) {
                client.sendResponse("ERROR", "You do not have permission to use this command.");
                return;
            }

            java.util.Map<String, String> map = mapper.convertValue(data, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});

            String itemName = map.get("itemName");
            String description = map.get("description");
            double startingPrice = Double.parseDouble(map.get("startingPrice"));
            double bidIncrement = Double.parseDouble(map.get("bidIncrement"));
            int durationMinutes = Integer.parseInt(map.get("durationMinutes"));

            model.Item item = new model.Art("ITM-" + System.currentTimeMillis(), itemName, description, startingPrice);

            // DIRECTLY USE AUTHENTICATED USER
            controller.ServerSellerController sellerCtrl = new controller.ServerSellerController();
            model.Auction newAuction = sellerCtrl.addAuction(authenticatedUser, item, bidIncrement, durationMinutes);

            if (newAuction != null) {
                newAuction.setStatus(model.Auction.STATUS_RUNNING);
                AuctionManager.addAuctionToMonitor(newAuction);

                System.out.println("[System]: Seller \"" + YELLOW + authenticatedUser.getName() + RESET + "\" has created an auction");

                String alertMsg = "[System]: Seller \"" + authenticatedUser.getName() + "\" has created an auction for \"" + YELLOW + itemName + RESET + "\" - " + GREEN + startingPrice + RESET + " VND";
                ClientManager.broadcast("CLI_BROADCAST", alertMsg, client);

                client.sendResponse("CREATE_SUCCESS", "Successfully created auction.");
                ClientManager.broadcast("NEW_AUCTION_ADDED", newAuction, null);
            } else {
                client.sendResponse("ERROR", "Cannot create auction due to database error.");
            }

        } catch (Exception e) {
            System.out.println("[System](AuctionActionHandler): Error: " + RED + e.getMessage() + RESET);
            client.sendResponse("ERROR", "Invalid data to create auction.");
        }
    }
}