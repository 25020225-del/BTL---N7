package gui.process;

import client.handler.AuctionEventBus;
import client.handler.ClientPaymentHandler;

/**
 * Memory management boundary guard. Explicitly decouples persistent event registry tables
 * during session context swaps to neutralize lifecycle reference leaks inside UI controllers.
 */
public final class RemoveEventBus {

    private RemoveEventBus() {
    }

    /**
     * Resets subscription registries bound to standard user session interactive operations.
     * Prevents transient event propagation into orphaned presentation components.
     */
    public static void forUser() {
        AuctionEventBus.removeAllListeners(AuctionEventBus.AUCTION_CREATED);
        AuctionEventBus.removeAllListeners(AuctionEventBus.DEPOSIT_SUCCESS);
        AuctionEventBus.removeAllListeners(AuctionEventBus.GENERAL_ERROR);
        AuctionEventBus.removeAllListeners(ClientPaymentHandler.PAYMENT_CONFIRM_REQUIRED);

        AuctionEventBus.removeAllListeners(AuctionEventBus.FETCH_AUCTIONS_SUCCESS);
        AuctionEventBus.removeAllListeners(AuctionEventBus.FETCH_TRANSACTIONS_SUCCESS);
        AuctionEventBus.removeAllListeners(AuctionEventBus.FETCH_WALLET_SUCCESS);
        AuctionEventBus.removeAllListeners(AuctionEventBus.FETCH_USERS_SUCCESS);
        AuctionEventBus.removeAllListeners(AuctionEventBus.ADMIN_ACTION_SUCCESS);

        AuctionEventBus.removeAllListeners("NEW_AUCTION_ADDED");
        AuctionEventBus.removeAllListeners("REMOVE_AUCTION");
        AuctionEventBus.removeAllListeners("DELETE_SUCCESS");
        AuctionEventBus.removeAllListeners("EDIT_SUCCESS");

        // Dọn dẹp các listener liên quan đến TOTP / 2FA khi người dùng đăng xuất
        AuctionEventBus.removeAllListeners("SETUP_2FA_SUCCESS");
        AuctionEventBus.removeAllListeners("CONFIRM_2FA_SUCCESS");
        AuctionEventBus.removeAllListeners("CANCEL_2FA_SUCCESS");
        AuctionEventBus.removeAllListeners("DISABLE_2FA_SUCCESS");
        AuctionEventBus.removeAllListeners("UPDATE_TOTP_PREFS_SUCCESS");
    }

    /**
     * Resets subscription registries bound to authoritative administrative sessions.
     */
    public static void forAdmin() {
        AuctionEventBus.removeAllListeners(AuctionEventBus.ADMIN_ACTION_SUCCESS);
        AuctionEventBus.removeAllListeners(AuctionEventBus.FETCH_AUCTIONS_SUCCESS);
        AuctionEventBus.removeAllListeners(AuctionEventBus.PRICE_UPDATED);
        AuctionEventBus.removeAllListeners(AuctionEventBus.FETCH_TRANSACTIONS_SUCCESS);
        AuctionEventBus.removeAllListeners(AuctionEventBus.GENERAL_ERROR);
        AuctionEventBus.removeAllListeners("AUCTION_STATUS_CHANGED");
    }
}