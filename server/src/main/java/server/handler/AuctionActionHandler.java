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
import service.CloudinaryService;
import utils.JacksonConfig;

import java.time.LocalDateTime;

import static utils.ConsoleColors.*;

/**
 * Handles requests from clients to create new auction sessions.
 */
public class AuctionActionHandler implements CommandHandler {

    private final ObjectMapper mapper = JacksonConfig.mapper();

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
            Auction auction = mapper.convertValue(data, new com.fasterxml.jackson.core.type.TypeReference<Auction>() {});

            String itemName = auction.getItem().getItemName();
            String description = auction.getItem().getDescription();
            String imageUrl = CloudinaryService.uploadImage(auction.getItem().getFile());
            double startingPrice = auction.getItem().getStartingPrice();
            double bidIncrement = auction.getBidIncrement();

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime reqStart = auction.getStartTime();
            LocalDateTime reqEnd = auction.getEndTime();

            if (reqStart == null || reqEnd == null) {
                client.sendResponse("ERROR", "Invalid time format.");
                return;
            }

            // Calculate the requested duration explicitly sent by the client
            long durationMinutes = java.time.Duration.between(reqStart, reqEnd).toMinutes();
            final long MAX_DURATION_MINUTES = 43200; // 30 days

            if (durationMinutes <= 0) {
                client.sendResponse("ERROR", "Invalid duration.");
                return;
            }
            if (durationMinutes > MAX_DURATION_MINUTES) {
                durationMinutes = MAX_DURATION_MINUTES; // Clamp to 30 days securely
            }

            // Validate Start Time constraints
            // Allowing a 5-minute leeway to account for network delay between Client's 'now' and Server's 'now'
            if (reqStart.isAfter(now.plusMinutes(5)) && reqStart.isBefore(now.plusDays(1).minusMinutes(5))) {
                client.sendResponse("ERROR", "Pre-set time must be 24 hours behind current time.");
                return;
            }
            //If the start time is in the past (or exactly 'now' from client), normalize it to server's exact 'now'
            if (reqStart.isBefore(now.plusMinutes(5))) reqStart = now;

            //-----------------------------------------------------------------------------------------------------

            // Extract item type if provided by the GUI dropdown, otherwise default to TANGIBLE
            //String itemType = map.containsKey("itemType") ? map.get("itemType") : ItemFactory.TYPE_TANGIBLE;
            String itemType = ItemFactory.TYPE_TANGIBLE;

            String newItemId = "ITM-" + System.currentTimeMillis();

            // FACTORY PATTERN APPLIED: Dynamically create the item based on its generalized category
            Item item = ItemFactory.createItem(itemType, newItemId, itemName, description, startingPrice);
            item.setImageUrl(imageUrl);

            // Forward the creation request to the Seller Controller
            controller.ServerSellerController sellerCtrl = new controller.ServerSellerController();
            Auction newAuction = sellerCtrl.addAuction(authenticatedUser, item, bidIncrement, reqStart, (int) durationMinutes);

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