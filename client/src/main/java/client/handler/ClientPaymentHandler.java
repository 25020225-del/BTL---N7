package client.handler;

import client.network.NetworkClient;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes payment and withdrawal lifecycle events to the {@link AuctionEventBus}.
 */
public class ClientPaymentHandler implements ResponseHandler {

    private static final Logger log = LoggerFactory.getLogger(ClientPaymentHandler.class);

    public static final String PAYMENT_CONFIRM_REQUIRED = "PAYMENT_CONFIRM_REQUIRED";
    public static final String REQUIRE_TOTP_PAYMENT = "REQUIRE_TOTP_PAYMENT";
    public static final String INVALID_TOTP = "INVALID_TOTP";
    public static final String WITHDRAW_REQUEST_SUCCESS = "WITHDRAW_REQUEST_SUCCESS";
    public static final String WITHDRAW_APPROVED = "WITHDRAW_APPROVED";
    public static final String WITHDRAW_REJECTED = "WITHDRAW_REJECTED";

    @Override
    public void handle(NetworkMessage message, NetworkClient client) throws Exception {
        String command = message.getCommand();
        log.info("Payment event received: {}", command);

        switch (command) {
            case "PAYMENT_CONFIRM_REQUIRED" -> AuctionEventBus.fireEvent(PAYMENT_CONFIRM_REQUIRED, message.getData());
            case "DEPOSIT_SUCCESS" -> AuctionEventBus.fireEvent(AuctionEventBus.DEPOSIT_SUCCESS, message.getData());
            case "REQUIRE_TOTP_PAYMENT", "INVALID_TOTP" -> AuctionEventBus.fireEvent(command, message);
            case "WITHDRAW_REQUEST_SUCCESS", "WITHDRAW_APPROVED", "WITHDRAW_REJECTED" -> {
                AuctionEventBus.fireEvent(command, message);
            }
            default -> log.warn("Unrecognized payment command: {}", command);
        }
    }
}