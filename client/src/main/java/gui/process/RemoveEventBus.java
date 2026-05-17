package gui.process;

import client.handler.AuctionEventBus;
import client.handler.ClientPaymentHandler;

public class RemoveEventBus {
    public static void forUser(){
        AuctionEventBus.removeAllListeners(AuctionEventBus.AUCTION_CREATED);
        AuctionEventBus.removeAllListeners(AuctionEventBus.DEPOSIT_SUCCESS);
        AuctionEventBus.removeAllListeners(AuctionEventBus.GENERAL_ERROR);
        AuctionEventBus.removeAllListeners(ClientPaymentHandler.PAYMENT_CONFIRM_REQUIRED);

        AuctionEventBus.removeAllListeners("FETCH_AUCTIONS_SUCCESS");
        AuctionEventBus.removeAllListeners("FETCH_TRANSACTIONS_SUCCESS");
        AuctionEventBus.removeAllListeners("FETCH_WALLET_SUCCESS");
        AuctionEventBus.removeAllListeners("NEW_AUCTION_ADDED");
        AuctionEventBus.removeAllListeners("REMOVE_AUCTION");
        AuctionEventBus.removeAllListeners("DELETE_SUCCESS");
        AuctionEventBus.removeAllListeners("EDIT_SUCCESS");
        AuctionEventBus.removeAllListeners("FETCH_USERS_SUCCESS");
        AuctionEventBus.removeAllListeners("ADMIN_ACTION_SUCCESS");
    }
    public static void forAdmin() {
        //updating...
    }
}
