package client.handler;

import client.network.NetworkClient;
import client.utils.ErrorParser;
import gui.process.AlertUtils;
import javafx.application.Platform;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Central dispatcher responsible for routing incoming network commands to their
 * respective {@link ResponseHandler} implementations.
 * Maps operational codes in O(1) time complexity and manages application-wide
 * error boundary fallbacks for unhandled execution failures.
 */
public class ResponseDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ResponseDispatcher.class);
    private final Map<String, ResponseHandler> handlers = new HashMap<>();

    /**
     * Initializes the dispatcher subsystem and registers all structural message-to-handler mappings.
     */
    public ResponseDispatcher() {
        registerSystemHandlers();
        registerAuctionHandlers();
        registerPaymentHandlers();
        registerUserHandlers();
        registerAdminHandlers();
    }

    private void registerSystemHandlers() {
        ClientSystemHandler systemHandler = new ClientSystemHandler();
        handlers.put("REDIRECT", systemHandler);
        handlers.put("KICKED", systemHandler);
        handlers.put("TIME_SYNC_ACK", systemHandler);
        handlers.put("GENERAL_ERROR", systemHandler);

        ResponseHandler errorHandler = (message, client) -> {
            if (client.getOnMessageReceived() != null) {
                Platform.runLater(() -> client.getOnMessageReceived().accept(message));
            } else {
                String errMsg = ErrorParser.parse(message.getData());
                log.warn("Server returned ERROR: {}", errMsg);
                Platform.runLater(() -> AlertUtils.showError("Lỗi từ Server", errMsg));
            }
        };
        handlers.put("ERROR", errorHandler);
    }

    private void registerAuctionHandlers() {
        ClientAuctionHandler auctionHandler = new ClientAuctionHandler();
        handlers.put("CLI_BROADCAST", auctionHandler);
        handlers.put("CREATE_SUCCESS", auctionHandler);
        handlers.put("CHAT", auctionHandler);
        handlers.put("UPDATE_AUCTION_PRICE", auctionHandler);
        handlers.put("AUCTION_STATUS_CHANGED", auctionHandler);
    }

    private void registerPaymentHandlers() {
        ClientPaymentHandler paymentHandler = new ClientPaymentHandler();
        handlers.put("PAYMENT_REDIRECT", paymentHandler);
        handlers.put("DEPOSIT_SUCCESS", paymentHandler);
        handlers.put("REQUIRE_TOTP_PAYMENT", paymentHandler);
        handlers.put("INVALID_TOTP", paymentHandler);
        handlers.put("WITHDRAW_REQUEST_SUCCESS", paymentHandler);
        handlers.put("WITHDRAW_APPROVED", paymentHandler);
        handlers.put("WITHDRAW_REJECTED", paymentHandler);
    }

    private void registerUserHandlers() {
        ClientUserHandler userHandler = new ClientUserHandler();
        handlers.put(AuctionEventBus.FETCH_AUCTIONS_SUCCESS, userHandler);
        handlers.put(AuctionEventBus.FETCH_MY_AUCTIONS_SUCCESS, userHandler);
        handlers.put(AuctionEventBus.FETCH_TRANSACTIONS_SUCCESS, userHandler);
        handlers.put("NEW_AUCTION_ADDED", userHandler);
        handlers.put("REMOVE_AUCTION", userHandler);
        handlers.put("EDIT_SUCCESS", userHandler);
        handlers.put("DELETE_SUCCESS", userHandler);
        handlers.put("FETCH_WALLET_SUCCESS", userHandler);
        handlers.put("FETCH_USERS_SUCCESS", userHandler);
        handlers.put("SETUP_2FA_SUCCESS", userHandler);
        handlers.put("CONFIRM_2FA_SUCCESS", userHandler);
        handlers.put("DISABLE_2FA_SUCCESS", userHandler);
        handlers.put("CANCEL_2FA_SUCCESS", userHandler);
        handlers.put("UPDATE_TOTP_PREFS_SUCCESS", userHandler);
    }

    private void registerAdminHandlers() {
        ResponseHandler adminHandler = (message, client) ->
                AuctionEventBus.fireEvent(message.getCommand(), message);

        handlers.put("FETCH_WITHDRAW_REQUESTS_SUCCESS", adminHandler);
        handlers.put("WITHDRAW_ACTION_SUCCESS", adminHandler);
        handlers.put("CANCEL_AUCTION_SUCCESS", adminHandler);
        handlers.put("TOGGLE_GOOD_STATUS", adminHandler);
        handlers.put("TOGGLE_GOOD_SUCCESS", adminHandler);
    }

    /**
     * Intercepts the inbound network message, identifies its registered handler,
     * and delegates execution flow to the presentation layer.
     *
     * @param message the incoming network data packet to be evaluated
     * @param client  the active client session infrastructure
     */
    public void dispatch(NetworkMessage message, NetworkClient client) {
        String command = message.getCommand();
        log.debug("Dispatching command: {}", command);

        ResponseHandler handler = handlers.get(command);

        if (handler != null) {
            try {
                handler.handle(message, client);
            } catch (Exception e) {
                log.error("Error handling command \"{}\": {}", command, e.getMessage(), e);
                Platform.runLater(() ->
                        AlertUtils.showError(
                                "System Error",
                                "A local error occurred while processing server data: " + e.getMessage()
                        )
                );
            }
        } else {
            if (client.getOnMessageReceived() != null) {
                Platform.runLater(() -> client.getOnMessageReceived().accept(message));
            } else {
                log.warn("No handler or callback registered for command: {}", command);
            }
        }
    }
}