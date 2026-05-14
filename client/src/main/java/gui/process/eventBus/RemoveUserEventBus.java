package gui.process.eventBus;

import client.handler.AuctionEventBus;
import client.handler.ClientPaymentHandler;

public class RemoveUserEventBus {
    public static void removeUserEventBus(){
        AuctionEventBus.removeAllListeners(AuctionEventBus.AUCTION_CREATED);
        AuctionEventBus.removeAllListeners(AuctionEventBus.DEPOSIT_SUCCESS);
        AuctionEventBus.removeAllListeners(AuctionEventBus.GENERAL_ERROR);
        AuctionEventBus.removeAllListeners(ClientPaymentHandler.PAYMENT_CONFIRM_REQUIRED);

        AuctionEventBus.removeAllListeners("FETCH_AUCTIONS_SUCCESS");
    }
}
