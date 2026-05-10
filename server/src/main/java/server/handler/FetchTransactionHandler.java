package server.handler;

import database.dao.BidDAO;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ClientHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FetchTransactionHandler implements CommandHandler{
    private static final Logger logger = LoggerFactory.getLogger(FetchTransactionHandler.class);
    private BidDAO bidDAO;
    private List<Map<String, Object>> transactionList = new ArrayList<>();

    public FetchTransactionHandler(BidDAO bidDAO) {
        this.bidDAO = bidDAO;
    }

    @Override
    public void handle (NetworkMessage networkMessage, ClientHandler client){
        String command = networkMessage.getCommand();

        try {
            switch (command) {
                case "FETCH_TRANSACTIONS" -> {
                    transactionList = bidDAO.getAllTrancactionsFromAuctionID((String) networkMessage.getData());
                }
            }
            logger.info("Fetching transactions from Auction ID: " + networkMessage.getData());
            client.sendResponse("FETCH_TRANSACTIONS_SUCCESS", transactionList);
        } catch (Exception e) {
            logger.error(e.getMessage());
            client.sendResponse("ERROR", "Internal database error");
        }
    }


}
