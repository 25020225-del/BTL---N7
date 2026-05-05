package server.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * Handles requests for retrieving auction listings from the database.
 * This class identifies whether a user is requesting public active auctions
 * or administrative pending auctions and formats the data for client-side display,
 * including time conversions for countdown timers.
 */
public class FetchAuctionsHandler implements CommandHandler {
    private static final Logger log = LoggerFactory.getLogger(FetchAuctionsHandler.class);

    /**
     * Processes FETCH_AUCTIONS and FETCH_PENDING_AUCTIONS commands.
     * For pending auctions, it enforces a security check to ensure the
     * requesting user has administrative privileges.
     *
     * @param message The network message containing the specific fetch command.
     * @param client  The client handler session requesting the data.
     */
    @Override
    public void handle(NetworkMessage message, ClientHandler client) {
        String command = message.getCommand();
        String sql = "";

        if ("FETCH_AUCTIONS".equals(command)) {
            sql = "SELECT id, item_name, description, starting_price, current_price, end_time, image_url FROM auctions WHERE status IN ('RUNNING', 'OPEN')";

        } else if ("FETCH_PENDING_AUCTIONS".equals(command)) {
            if (client.getUser() != null && client.getUser().getRole().equalsIgnoreCase("ADMIN")) {
                sql = "SELECT id, item_name, description, starting_price, current_price, end_time, image_url FROM auctions WHERE status = 'PENDING_APPROVAL'";
            } else {
                client.sendResponse("ERROR", "You do not have permission to view pending auctions.");
                return;
            }
        } else {
            return;
        }

        List<Map<String, Object>> activeAuctions = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> auctionData = new HashMap<>();
                auctionData.put("id", rs.getString("id"));
                auctionData.put("itemName", rs.getString("item_name"));
                auctionData.put("description", rs.getString("description"));
                auctionData.put("startingPrice", rs.getDouble("starting_price"));
                auctionData.put("currentPrice", rs.getDouble("current_price"));

                LocalDateTime endTime = LocalDateTime.parse(rs.getString("end_time"));
                long endTimeMillis = endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                auctionData.put("endTime", endTimeMillis);
                auctionData.put("imageUrl", rs.getString("image_url"));

                activeAuctions.add(auctionData);
            }

            client.sendResponse("FETCH_AUCTIONS_SUCCESS", activeAuctions);
            log.info("Sent auction list to {}", client.getClientName());

        } catch (Exception e) {
            log.warn("Getting auction list error: {}", e.getMessage());
            client.sendResponse("ERROR", "Cannot load auction list.");
        }
    }
}