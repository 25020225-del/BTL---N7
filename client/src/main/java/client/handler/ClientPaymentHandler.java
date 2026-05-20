package client.handler;

import client.network.NetworkClient;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;
import java.util.Map;

/**
 * Handles all payment- and withdrawal-related responses from the server.
 *
 * <p>All events are forwarded to the {@link AuctionEventBus} so that
 * UI controllers can register their own listeners without coupling to the
 * network layer.</p>
 *
 * <p><b>Event constants defined here (all public static final String):</b></p>
 * <ul>
 *   <li>{@link #PAYMENT_CONFIRM_REQUIRED} — PayPal redirect opened.</li>
 *   <li>{@link #REQUIRE_TOTP_PAYMENT}    — Server requests TOTP before processing.</li>
 *   <li>{@link #INVALID_TOTP}            — Submitted TOTP code was wrong.</li>
 *   <li>{@link #WITHDRAW_REQUEST_SUCCESS} — Withdrawal request created successfully.</li>
 *   <li>{@link #WITHDRAW_APPROVED}        — Admin approved a withdrawal (real-time).</li>
 *   <li>{@link #WITHDRAW_REJECTED}        — Admin rejected a withdrawal (real-time).</li>
 * </ul>
 */
public class ClientPaymentHandler implements ResponseHandler {

    private static final Logger log = LoggerFactory.getLogger(ClientPaymentHandler.class);

    /** Fired when PayPal URL is opened and user needs to confirm in browser. */
    public static final String PAYMENT_CONFIRM_REQUIRED  = "PAYMENT_CONFIRM_REQUIRED";

    /** Fired when server requests a TOTP code before a payment/withdrawal. */
    public static final String REQUIRE_TOTP_PAYMENT      = "REQUIRE_TOTP_PAYMENT";

    /** Fired when the submitted TOTP code is rejected by the server. */
    public static final String INVALID_TOTP              = "INVALID_TOTP";

    /** Fired when a withdrawal request has been successfully created (PENDING). */
    public static final String WITHDRAW_REQUEST_SUCCESS  = "WITHDRAW_REQUEST_SUCCESS";

    /** Fired (real-time) when an admin approves the user's withdrawal request. */
    public static final String WITHDRAW_APPROVED         = "WITHDRAW_APPROVED";

    /** Fired (real-time) when an admin rejects the user's withdrawal request. */
    public static final String WITHDRAW_REJECTED         = "WITHDRAW_REJECTED";

    @Override
    @SuppressWarnings("unchecked")
    public void handle(NetworkMessage message, NetworkClient client) throws Exception {
        String command = message.getCommand();

        switch (command) {
            case "PAYMENT_REDIRECT" -> {
                Map<String, String> responseData = (Map<String, String>) message.getData();
                String url = responseData.get("url");
                log.info("Opening payment URL: {}", url);
                if (Desktop.isDesktopSupported()
                        && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                }
                AuctionEventBus.fireEvent(PAYMENT_CONFIRM_REQUIRED, responseData);
            }

            case "DEPOSIT_SUCCESS" -> {
                log.info("Deposit success: {}", message.getData());
                AuctionEventBus.fireEvent(AuctionEventBus.DEPOSIT_SUCCESS, message.getData());
            }

            case "REQUIRE_TOTP_PAYMENT" -> {
                // Forward to EventBus; WalletController listens and shows dialog.
                AuctionEventBus.fireEvent(REQUIRE_TOTP_PAYMENT, message);
            }

            case "INVALID_TOTP" -> {
                // Forward to EventBus; WalletController shows error alert.
                AuctionEventBus.fireEvent(INVALID_TOTP, message);
            }

            // ── Withdrawal events [NEW] ─────────────────────────────────────
            case "WITHDRAW_REQUEST_SUCCESS" -> {
                log.info("Withdrawal request created: {}", message.getData());
                AuctionEventBus.fireEvent(WITHDRAW_REQUEST_SUCCESS, message);
            }

            case "WITHDRAW_APPROVED" -> {
                log.info("Withdrawal approved by admin: {}", message.getData());
                AuctionEventBus.fireEvent(WITHDRAW_APPROVED, message);
            }

            case "WITHDRAW_REJECTED" -> {
                log.info("Withdrawal rejected by admin: {}", message.getData());
                AuctionEventBus.fireEvent(WITHDRAW_REJECTED, message);
            }
        }
    }
}