package server.ClientHandlerExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import network.NetworkMessage;
import server.ClientHandler;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;

import static utils.ConsoleColors.*;

public class AuctionActionHandler implements CommandHandler {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void handle(NetworkMessage message, ClientHandler client) {
        if ("CREATE_AUCTION".equals(message.getCommand())) {
            processCreateAuction(message.getData(), client);
        }
    }

    private void processCreateAuction(Object data, ClientHandler client) {
        try {
            java.util.Map<String, String> map = mapper.convertValue(data, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});

            String itemName = map.get("itemName");
            String description = map.get("description");
            double startingPrice = Double.parseDouble(map.get("startingPrice"));
            double bidIncrement = Double.parseDouble(map.get("bidIncrement"));
            int durationMinutes = Integer.parseInt(map.get("durationMinutes"));

            model.Item item = new model.Art("ITM-" + System.currentTimeMillis(), itemName, description, startingPrice);
            model.User currentSeller = new model.User("U-" + client.getClientName(), client.getClientName(), "", client.getClientName(), "SELLER");
            currentSeller.setGood(true);

            controller.ServerSellerController sellerCtrl = new controller.ServerSellerController();
            model.Auction newAuction = sellerCtrl.addAuction(currentSeller, item, bidIncrement, durationMinutes);

            if (newAuction != null) {
                newAuction.setStatus(model.Auction.STATUS_RUNNING);
                AuctionManager.addAuctionToMonitor(newAuction);

                System.out.println("[System]: Seller \"" + YELLOW + client.getClientName() + RESET + "\" has created an auction");
                System.out.println("[System]: Item: " + YELLOW + itemName + RESET + " - Starting Price: " + YELLOW + startingPrice + " VND" + RESET);

                String alertMsg = "[System]: Seller \"" + client.getClientName() + "\" has created an auction of \"" + YELLOW + itemName + RESET + "\" with the price of " + YELLOW + startingPrice + RESET + " VND";
                ClientManager.broadcast("CLI_BROADCAST", alertMsg, client);

                client.sendResponse("CREATE_SUCCESS", "Successfully created auction");
            } else {
                client.sendResponse("ERROR", "Database Error when creating auction");
            }

        } catch (Exception e) {
            System.out.println("[Error]: " + "Error when trying CREATE_AUCTION: " + RED + e.getMessage() + RESET);
            client.sendResponse("ERROR", "Auction creation data is invalid");
        }
    }
}