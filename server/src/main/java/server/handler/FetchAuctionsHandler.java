package server.handler;

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
        String command = message.getCommand();
        String sql = "";

        if ("FETCH_AUCTIONS".equals(command)) {
            sql = "SELECT id, item_name, current_price, end_time FROM auctions WHERE status IN ('RUNNING', 'OPEN')";

        } else if ("FETCH_PENDING_AUCTIONS".equals(command)) {
            if (client.getUser() != null && client.getUser().getRole().equalsIgnoreCase("ADMIN")) {
                sql = "SELECT id, item_name, current_price, end_time FROM auctions WHERE status = 'PENDING_APPROVAL'";
            } else {
                client.sendResponse("ERROR", "You do not have permission to view pending auctions.");
                return;
            }
        } else {
            return; // If neither of the two commands above applies, skip it
        }

        List<Map<String, Object>> activeAuctions = new ArrayList<>();

        // 2. Run SQL and export data
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> auctionData = new HashMap<>();
                auctionData.put("id", rs.getString("id"));
                auctionData.put("itemName", rs.getString("item_name"));
                auctionData.put("currentPrice", rs.getDouble("current_price"));

                // Convert the time to milliseconds for the countdown UI
                LocalDateTime endTime = LocalDateTime.parse(rs.getString("end_time"));
                long endTimeMillis = endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                auctionData.put("endTime", endTimeMillis);

                activeAuctions.add(auctionData);
            }

            // NOTE: Return a single “FETCH_AUCTIONS_SUCCESS” flag to make it easier for the client to reuse the UI
            client.sendResponse("FETCH_AUCTIONS_SUCCESS", activeAuctions);
            System.out.println("[System]: Sent auction list (" + command + ") to \"" + YELLOW + client.getClientName() + RESET + "\"");

        } catch (Exception e) {
            System.out.println("[System](FetchAuctionHandler): Getting auction list error: " + RED + e.getMessage() + RESET);
            client.sendResponse("ERROR", "Cannot load auction list.");
        }
    }
}