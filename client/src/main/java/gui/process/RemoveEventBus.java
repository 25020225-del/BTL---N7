package gui.process;

import client.handler.AuctionEventBus;
import client.handler.ClientPaymentHandler;

/**
 * Utility class for cleaning up all {@link AuctionEventBus} listeners tied to
 * a user or admin session.
 *
 * <p>Must be called when a user logs out to prevent listener leaks and stale
 * event deliveries to old controllers.</p>
 *
 * <p>This is a stateless utility class and must not be instantiated.</p>
 */
public final class RemoveEventBus {

    /** Private constructor — utility class, not instantiable. */
    private RemoveEventBus() {}

    /**
     * Removes all EventBus listeners registered during an active user session.
     * Should be called from the sign-out handler before navigating back to the login screen.
     *
     * <p><b>FIX:</b> Replaced all raw string literals with constants from {@link AuctionEventBus}
     * and {@link ClientPaymentHandler}. This prevents silent bugs if command strings are ever renamed.</p>
     */
    public static void forUser() {
        // Core session events
        AuctionEventBus.removeAllListeners(AuctionEventBus.AUCTION_CREATED);
        AuctionEventBus.removeAllListeners(AuctionEventBus.DEPOSIT_SUCCESS);
        AuctionEventBus.removeAllListeners(AuctionEventBus.GENERAL_ERROR);
        AuctionEventBus.removeAllListeners(ClientPaymentHandler.PAYMENT_CONFIRM_REQUIRED);

        // FIX: Replace raw strings with constants from AuctionEventBus
        AuctionEventBus.removeAllListeners(AuctionEventBus.FETCH_AUCTIONS_SUCCESS);
        AuctionEventBus.removeAllListeners(AuctionEventBus.FETCH_TRANSACTIONS_SUCCESS);
        AuctionEventBus.removeAllListeners(AuctionEventBus.FETCH_WALLET_SUCCESS);
        AuctionEventBus.removeAllListeners(AuctionEventBus.FETCH_USERS_SUCCESS);
        AuctionEventBus.removeAllListeners(AuctionEventBus.ADMIN_ACTION_SUCCESS);

        // Auction lifecycle events — add constants to AuctionEventBus if not already present
        AuctionEventBus.removeAllListeners("NEW_AUCTION_ADDED");
        AuctionEventBus.removeAllListeners("REMOVE_AUCTION");
        AuctionEventBus.removeAllListeners("DELETE_SUCCESS");
        AuctionEventBus.removeAllListeners("EDIT_SUCCESS");
    }

    /**
     * Removes all EventBus listeners registered during an active admin session.
     * Extend this method as admin-specific event subscriptions are added.
     */
    public static void forAdmin() {
        // Admin-specific cleanup — extend as needed
        AuctionEventBus.removeAllListeners(AuctionEventBus.ADMIN_ACTION_SUCCESS);
    }
}
