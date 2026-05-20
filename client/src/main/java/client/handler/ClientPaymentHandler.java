package client.handler;

import client.network.NetworkClient;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.net.URI;
import java.util.Map;

public class ClientPaymentHandler implements ResponseHandler {
    private static final Logger log =  LoggerFactory.getLogger(ClientPaymentHandler.class);

    public static final String PAYMENT_CONFIRM_REQUIRED = "PAYMENT_CONFIRM_REQUIRED";
    public static final String REQUIRE_TOTP_PAYMENT = "REQUIRE_TOTP_PAYMENT";
    public static final String INVALID_TOTP         = "INVALID_TOTP";

    @Override
    @SuppressWarnings("unchecked")
    public void handle(NetworkMessage message, NetworkClient client) throws Exception {
        String command = message.getCommand();
        Object data = message.getData();

        switch (command) {
            case "PAYMENT_REDIRECT" -> {
                Map<String, String> responseData = (Map<String, String>) data;
                String url = responseData.get("url");

                log.info("Open {} to complete payment.", url);

                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                }

                AuctionEventBus.fireEvent(PAYMENT_CONFIRM_REQUIRED, data);

            }
            case "DEPOSIT_SUCCESS" -> {
                log.info(data.toString());
                AuctionEventBus.fireEvent(AuctionEventBus.DEPOSIT_SUCCESS, data);
            }
            case "REQUIRE_TOTP_PAYMENT" -> {
                // Forward lên EventBus; WalletController sẽ lắng nghe
                AuctionEventBus.fireEvent(REQUIRE_TOTP_PAYMENT, message);
            }
            case "INVALID_TOTP" -> {
                // Forward lên EventBus; WalletController hiển thị lỗi và cho nhập lại
                AuctionEventBus.fireEvent(INVALID_TOTP, message);
            }
        }
    }
}