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
 * Command route handler managing data retrieval and transformation loops for auction records.
 * Translates underlying database timestamp boundaries into reactive client countdown states.
 */
public class FetchAuctionsHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(FetchAuctionsHandler.class);
    private final database.dao.AuctionDAO auctionDAO;

    public FetchAuctionsHandler(database.dao.AuctionDAO auctionDAO) {
        this.auctionDAO = auctionDAO;
    }

    @Override
    public void handle(NetworkMessage message, ClientHandler client) {
        String command = message.getCommand();
        String responseCommand = "FETCH_AUCTIONS_SUCCESS";

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
                responseCommand = "FETCH_MY_AUCTIONS_SUCCESS";

            } else if ("FETCH_PENDING_AUCTIONS".equals(command)) {
                if (client.getUser() != null && client.getUser().getRole().equalsIgnoreCase("ADMIN")) {
                    auctionList = auctionDAO.getAuctionsByStatus("PENDING_APPROVAL");
                } else {
                    client.sendResponse("ERROR", "You do not have permission to view pending auctions.");
                    return;
                }
            }

            LocalDateTime now = LocalDateTime.now();
            for (Map<String, Object> map : auctionList) {
                Object endTimeObj = map.get("endTime");
                if (endTimeObj instanceof Number n) {
                    LocalDateTime end = Instant.ofEpochMilli(n.longValue())
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime();
                    long secs = Duration.between(now, end).getSeconds();
                    map.put("secondsRemaining", Math.max(0, secs));
                } else {
                    map.put("secondsRemaining", 0L);
                }
            }

            client.sendResponse(responseCommand, auctionList);

        } catch (Exception e) {
            log.error("Database error during auction fetch command execution [{}]: {}", command, e.getMessage(), e);
            client.sendResponse("ERROR", "Internal database error.");
        }
    }
}