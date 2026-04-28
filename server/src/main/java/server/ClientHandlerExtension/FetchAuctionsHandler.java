package server.ClientHandlerExtension;

import database.DatabaseManager;
import network.NetworkMessage;
import server.ClientHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static utils.ConsoleColors.*;

public class FetchAuctionsHandler implements CommandHandler {
    @Override
    public void handle(NetworkMessage message, ClientHandler client) {
        List<Map<String, Object>> activeAuctions = new ArrayList<>();
        // Select only sessions that are currently running or open
        String sql = "SELECT id, item_name, current_price, end_time FROM auctions WHERE status IN ('RUNNING', 'OPEN')";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> auctionData = new HashMap<>();
                auctionData.put("id", rs.getString("id"));
                auctionData.put("itemName", rs.getString("item_name"));
                auctionData.put("currentPrice", rs.getDouble("current_price"));

                // Parse date and time string into a Timestamp (milliseconds) for the CountdownClock widget
                LocalDateTime endTime = LocalDateTime.parse(rs.getString("end_time"));
                long endTimeMillis = endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                auctionData.put("endTime", endTimeMillis);

                activeAuctions.add(auctionData);
            }

            // Return the list to client
            client.sendResponse("FETCH_AUCTIONS_SUCCESS", activeAuctions);
            System.out.println("[System]: Sent auction list to \"" + YELLOW + client.getClientName() + RESET + "\"");

        } catch (Exception e) {
            System.out.println("[System](FetchAuctionsHandler): Auction list getting error: " + RED + e.getMessage() + RESET);
            client.sendResponse("ERROR", "Cannot load auctions from server");
        }
    }
}