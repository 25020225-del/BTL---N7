package gui;

import java.util.HashMap;
import java.util.Map;
import client.network.NetworkClient;

public class ClientSellerController {
    public void createAuction() {
        Map<String, String> auctionData = new HashMap<>();
        auctionData.put("itemName", "Lông dái Ronaldo");
        auctionData.put("description", "Còn thơm mùi nước đái");
        auctionData.put("startingPrice", "2500000000");
        auctionData.put("bidIncrement", "5000000");
        auctionData.put("durationMinutes", "69");

        System.out.println("[Log]: Sending creating auction request...");
        MainApplication.networkClient.sendMessage("CREATE_AUCTION", auctionData);
    }
}
