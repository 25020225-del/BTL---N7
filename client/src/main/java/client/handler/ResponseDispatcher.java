package client.handler;

import client.network.NetworkClient;
import client.utils.ErrorParser;
import gui.process.AlertHelper;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Central dispatcher that routes incoming JSON commands from the server
 * to their appropriate dedicated {@link ResponseHandler} implementations.
 *
 * <p>Uses a {@link HashMap} keyed by command string for O(1) dispatch.
 * Unregistered commands are forwarded to a per-connection fallback callback,
 * supporting dynamic flows such as Login and Registration.</p>
 *
 * <p>The dispatcher also serves as a safety net: any unhandled exception
 * from a handler is logged and surfaced to the user as an error alert.</p>
 */
public class ResponseDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ResponseDispatcher.class);

    private final Map<String, ResponseHandler> handlers = new HashMap<>();

    /**
     * Constructs the dispatcher and registers all known command-to-handler mappings.
     */
    public ResponseDispatcher() {
        registerSystemHandlers();
        registerHandlers();
        registerAuctionHandlers();
        registerPaymentHandlers();
        registerUserHandlers();
        registerAdminHandlers();
    }

    // ── Handler Registration ──────────────────────────────────────────────────

    /**
     * Registers all known server commands to their respective handler instances.
     * Each handler instance is shared across all commands it is responsible for.
     */
    private void registerHandlers() {
        registerSystemHandlers();
        registerAuctionHandlers();
        registerPaymentHandlers();
        registerUserHandlers();
    }

    private void registerSystemHandlers() {
        ClientSystemHandler systemHandler = new ClientSystemHandler();
        handlers.put("REDIRECT",      systemHandler);
        handlers.put("KICKED",        systemHandler);
        handlers.put("TIME_SYNC_ACK", systemHandler);
        handlers.put("GENERAL_ERROR", systemHandler);
        ResponseHandler errorHandler = (message, client) -> {
            if (client.getOnMessageReceived() != null) {
                Platform.runLater(() -> client.getOnMessageReceived().accept(message));
            } else {
                String errMsg = ErrorParser.parse(message.getData());
                log.warn("Server returned ERROR: {}", errMsg);
                Platform.runLater(() ->
                        AlertHelper.showAlert(
                                Alert.AlertType.ERROR,
                                "Lỗi từ Server",
                                errMsg
                        )
                );
            }
        };
        handlers.put("ERROR", errorHandler);
    }

    private void registerAuctionHandlers() {
        ClientAuctionHandler auctionHandler = new ClientAuctionHandler();
        handlers.put("CLI_BROADCAST",       auctionHandler);
        handlers.put("CREATE_SUCCESS",      auctionHandler);
        handlers.put("CHAT",                auctionHandler);
        handlers.put("UPDATE_AUCTION_PRICE", auctionHandler);
    }

    private void registerPaymentHandlers() {
        ClientPaymentHandler paymentHandler = new ClientPaymentHandler();
        handlers.put("PAYMENT_REDIRECT",    paymentHandler);
        handlers.put("DEPOSIT_SUCCESS",     paymentHandler);
        handlers.put("REQUIRE_TOTP_PAYMENT", paymentHandler);
        handlers.put("INVALID_TOTP",        paymentHandler);
        handlers.put("WITHDRAW_REQUEST_SUCCESS", paymentHandler);
        handlers.put("WITHDRAW_APPROVED",        paymentHandler);
        handlers.put("WITHDRAW_REJECTED",        paymentHandler);
    }

    private void registerUserHandlers() {
        ClientUserHandler userHandler = new ClientUserHandler();
        handlers.put("FETCH_AUCTIONS_SUCCESS",     userHandler);
        handlers.put("FETCH_TRANSACTIONS_SUCCESS", userHandler);
        handlers.put("NEW_AUCTION_ADDED",          userHandler);
        handlers.put("REMOVE_AUCTION",             userHandler);
        handlers.put("EDIT_SUCCESS",               userHandler);
        handlers.put("DELETE_SUCCESS",             userHandler);
        handlers.put("FETCH_WALLET_SUCCESS",       userHandler);
        handlers.put("FETCH_USERS_SUCCESS",        userHandler);
        handlers.put("SETUP_2FA_SUCCESS",          userHandler);
        handlers.put("CONFIRM_2FA_SUCCESS",        userHandler);
        handlers.put("DISABLE_2FA_SUCCESS",        userHandler);
        handlers.put("CANCEL_2FA_SUCCESS",         userHandler);
        handlers.put("UPDATE_TOTP_PREFS_SUCCESS",  userHandler);


    }
    private void registerAdminHandlers() {
        ResponseHandler adminHandler = (message, client) -> {
            switch (message.getCommand()) {
                case "FETCH_WITHDRAW_REQUESTS_SUCCESS" ->
                        AuctionEventBus.fireEvent("FETCH_WITHDRAW_REQUESTS_SUCCESS", message);
                case "WITHDRAW_ACTION_SUCCESS" ->
                        AuctionEventBus.fireEvent("WITHDRAW_ACTION_SUCCESS", message);
            }
        };
        handlers.put("FETCH_WITHDRAW_REQUESTS_SUCCESS", adminHandler);
        handlers.put("WITHDRAW_ACTION_SUCCESS",          adminHandler);
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    /**
     * Looks up the correct handler for the given message and delegates execution to it.
     *
     * <p>If no handler is registered, the message is forwarded to the client's
     * {@code onMessageReceived} callback (if set). This supports ad-hoc flows
     * like Login/Register that set their own temporary callbacks.</p>
     *
     * <p>Any exception thrown by a handler is caught here; it is logged for developers
     * and displayed as an error alert for end-users.</p>
     *
     * @param message The incoming {@link NetworkMessage} to dispatch.
     * @param client  The active {@link NetworkClient} passed to the handler.
     */
    public void dispatch(NetworkMessage message, NetworkClient client) {
        String command = message.getCommand();
        log.debug("Dispatching command: {}", command); // FIX: was System.out.println(command)

        ResponseHandler handler = handlers.get(command);

        if (handler != null) {
            try {
                handler.handle(message, client);
            } catch (Exception e) {
                // FIX: removed e.printStackTrace() — log.error with stack trace is sufficient
                log.error("Error handling command \"{}\": {}", command, e.getMessage(), e);
                Platform.runLater(() ->
                        AlertHelper.showAlert(
                                Alert.AlertType.ERROR,
                                "System Error",
                                "A local error occurred while processing server data: " + e.getMessage()
                        )
                );
            }
        } else {
            // Forward unregistered commands to any registered one-off callback (e.g., LoginController)
            if (client.getOnMessageReceived() != null) {
                Platform.runLater(() -> client.getOnMessageReceived().accept(message));
            } else {
                log.warn("No handler or callback registered for command: {}", command);
            }
        }
    }
}
