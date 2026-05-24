package server.handler;

import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ClientHandler;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles requests for retrieving auction listings from the database.
 * This class identifies whether a user is requesting public active auctions
 * or administrative pending auctions and formats the data for client-side display,
 * including time conversions for countdown timers.
 */
public class FetchAuctionsHandler implements CommandHandler {
    private static final Logger log = LoggerFactory.getLogger(FetchAuctionsHandler.class);
    private final database.dao.AuctionDAO auctionDAO;

    /**
     * Constructs the handler with necessary DAOs via Dependency Injection.
     *
     * @param auctionDAO The DAO for auction data retrieval.
     */
    public FetchAuctionsHandler(database.dao.AuctionDAO auctionDAO) {
        this.auctionDAO = auctionDAO;
    }

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

        try {
            List<Map<String, Object>> auctionList = new ArrayList<>();

            if ("FETCH_AUCTIONS".equals(command)) {
                auctionList = auctionDAO.getAuctionsByStatus("RUNNING", "OPEN");

            } else if ("FETCH_MY_AUCTIONS".equals(command)) {
                if (client.getUser() == null) {
                    client.sendResponse("ERROR", "Not authenticated.");
                    return;
                }
                auctionList = auctionDAO.getAuctionsBySeller(client.getUser().getId());
                // tính secondsRemaining như bình thường (vòng for bên dưới xử lý luôn)
                client.sendResponse("FETCH_MY_AUCTIONS_SUCCESS", auctionList);
                return;
            } else if ("FETCH_PENDING_AUCTIONS".equals(command)) {
                if (client.getUser() != null && client.getUser().getRole().equalsIgnoreCase("ADMIN")) {
                    auctionList = auctionDAO.getAuctionsByStatus("PENDING_APPROVAL");
                } else {
                    client.sendResponse("ERROR", "You do not have permission to view pending auctions.");
                    return;
                }
            }

            // Transform the end_time into a remaining seconds format for the client-side countdown
            LocalDateTime now = LocalDateTime.now();
            for (Map<String, Object> map : auctionList) {
                Object endTimeObj = map.get("endTime"); // epoch millis
                if (endTimeObj instanceof Number n) {
                    LocalDateTime end = Instant.ofEpochMilli(n.longValue())
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime();
                    long secs = Duration.between(LocalDateTime.now(), end).getSeconds();
                    map.put("secondsRemaining", Math.max(0, secs));
                } else {
                    map.put("secondsRemaining", 0L); // WAITING_FOR_BID: endTime null
                }
            }

            client.sendResponse("FETCH_AUCTIONS_SUCCESS", auctionList);

        } catch (Exception e) {
            log.error("Database error during auction fetch: {}", e.getMessage());
            client.sendResponse("ERROR", "Internal database error.");
        }
    }
}