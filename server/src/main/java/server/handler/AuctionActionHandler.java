package server.handler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.item.Item;
import model.item.ItemFactory;
import model.auction.Auction;
import model.user.User;
import network.NetworkMessage;
import server.ClientHandler;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;

import static utils.ConsoleColors.*;

/**
 * Handles requests from clients to create new auction sessions.
 */
public class AuctionActionHandler implements CommandHandler {

    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public void handle(NetworkMessage message, ClientHandler client) {
        if ("CREATE_AUCTION".equals(message.getCommand())) {
            processCreateAuction(message.getData(), client);
        }
    }

    /**
     * Processes the incoming JSON payload to construct a new auction.
     * Utilizes the ItemFactory to instantiate the correct item subtype.
     *
     * @param data   The JSON payload containing auction parameters.
     * @param client The client requesting the creation.
     */
    private void processCreateAuction(Object data, ClientHandler client) {
        try {
            // Retrieve user identification from the active login session
            User authenticatedUser = client.getUser();

            // Security check: Reject if the user is not authenticated
            if (authenticatedUser == null) {
                client.sendResponse("ERROR", "You do not have permission to use this command.");
                return;
            }

            // Extract data from the incoming JSON payload
            java.util.Map<String, String> map = mapper.convertValue(data, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});

            String itemName = map.get("itemName");
            String description = map.get("description");
            double startingPrice = Double.parseDouble(map.get("startingPrice"));
            double bidIncrement = Double.parseDouble(map.get("bidIncrement"));
            int durationMinutes = Integer.parseInt(map.get("durationMinutes"));

            // Extract item type if provided by the GUI dropdown, otherwise default to TANGIBLE
            String itemType = map.containsKey("itemType") ? map.get("itemType") : ItemFactory.TYPE_TANGIBLE;

            String newItemId = "ITM-" + System.currentTimeMillis();

            // FACTORY PATTERN APPLIED: Dynamically create the item based on its generalized category
            Item item = ItemFactory.createItem(itemType, newItemId, itemName, description, startingPrice);

            // Forward the creation request to the Seller Controller
            controller.ServerSellerController sellerCtrl = new controller.ServerSellerController();
            Auction newAuction = sellerCtrl.addAuction(authenticatedUser, item, bidIncrement, durationMinutes);

            if (newAuction != null) {
                newAuction.setStatus(Auction.STATUS_RUNNING);
                AuctionManager.addAuctionToMonitor(newAuction);

                System.out.println("[System]: Seller \"" + YELLOW + authenticatedUser.getName() + RESET + "\" has created an auction.");

                String alertMsg = "[System]: Seller \"" + authenticatedUser.getName() + "\" has created an auction for \"" + YELLOW + itemName + RESET + "\" - " + GREEN + startingPrice + RESET + " VND";
                ClientManager.broadcast("CLI_BROADCAST", alertMsg, client);

                client.sendResponse("CREATE_SUCCESS", "Successfully created auction.");

                // Broadcast the newly created auction to all clients for real-time UI updates
                ClientManager.broadcast("NEW_AUCTION_ADDED", newAuction, null);
            } else {
                client.sendResponse("ERROR", "Cannot create auction due to a database error.");
            }

        } catch (Exception e) {
            System.out.println("[System](AuctionActionHandler): Error parsing creation data: " + RED + e.getMessage() + RESET);
            client.sendResponse("ERROR", "Invalid data format provided for creating auction.");
        }
    }
}