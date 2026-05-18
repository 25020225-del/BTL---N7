package server.handler;

import database.dao.BidDAO;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ClientHandler;

import java.util.List;
import java.util.Map;

/**
 * Handles requests for fetching bid transaction history for a specific auction.
 */
public class FetchTransactionHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(FetchTransactionHandler.class);
    private final BidDAO bidDAO;

    public FetchTransactionHandler(BidDAO bidDAO) {
        this.bidDAO = bidDAO;
    }

    @Override
    public void handle(NetworkMessage networkMessage, ClientHandler client) {
        String auctionId = (String) networkMessage.getData();
        log.info("Fetching transactions for auction ID: {}", auctionId);

        try {
            List<Map<String, Object>> transactionList = bidDAO.getTransactionsForAuction(auctionId);
            client.sendResponse("FETCH_TRANSACTIONS_SUCCESS", transactionList);
        } catch (Exception e) {
            log.error("Failed to fetch transactions for auction {}", auctionId, e);
            client.sendResponse("ERROR", "Internal database error");
        }
    }
}