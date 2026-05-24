package client.handler;

import client.network.NetworkClient;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Routes real-time auction notifications (price updates, lifecycle state transitions)
 * to the registered listeners on the {@link AuctionEventBus}.
 */
public class ClientAuctionHandler implements ResponseHandler {

    private static final Logger log = LoggerFactory.getLogger(ClientAuctionHandler.class);

    @Override
    @SuppressWarnings("unchecked")
    public void handle(NetworkMessage message, NetworkClient client) throws Exception {
        String command = message.getCommand();

        switch (command) {
            case "UPDATE_AUCTION_PRICE" -> {
                log.debug("[Auction] Price update: {}", message.getData());
                AuctionEventBus.fireEvent(AuctionEventBus.PRICE_UPDATED, message.getData());
            }
            case "AUTOBID_SETUP_SUCCESS", "AUTOBID_ACTIVE" -> {
                log.info("[AutoBid] State update: {}", message.getData());
                AuctionEventBus.fireEvent(command, message.getData());
            }
            case "BID_SUCCESS" -> {
                AuctionEventBus.fireEvent(AuctionEventBus.BID_SUCCESS, message.getData());
            }
            case "AUCTION_STATUS_CHANGED" -> {
                Map<String, Object> statusData = (Map<String, Object>) message.getData();
                log.info("[Auction] Status changed: {}", statusData);
                AuctionEventBus.fireEvent("AUCTION_STATUS_CHANGED", statusData);
            }
            case "CLI_BROADCAST" -> {
                log.info("[Broadcast] {}", message.getData());
                AuctionEventBus.fireEvent("CLI_BROADCAST", message.getData());
            }
            case "CREATE_SUCCESS" -> {
                log.info("[Auction] Created: {}", message.getData());
                AuctionEventBus.fireEvent(AuctionEventBus.AUCTION_CREATED, message.getData());
            }
            case "CHAT" -> {
                log.debug("[Chat] {}", message.getData());
                AuctionEventBus.fireEvent("CHAT", message.getData());
            }
            default -> log.warn("Unrecognized auction command: {}", command);
        }
    }
}