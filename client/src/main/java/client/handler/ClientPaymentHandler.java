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

    @Override
    @SuppressWarnings("unchecked")
    public void handle(NetworkMessage message, NetworkClient client) throws Exception {
        String command = message.getCommand();
        Object data = message.getData();

        if ("PAYMENT_REDIRECT".equals(command)) {
            // The server returns the order ID and the PayPal payment URL
            Map<String, String> responseData = (Map<String, String>) data;
            String url = responseData.get("url");

            log.info("Open {} to complete payment.", url);

            // Open the browser automatically
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }

            // Fire event to let UI handle the confirmation dialog
            AuctionEventBus.fireEvent(PAYMENT_CONFIRM_REQUIRED, data);

        } else if ("DEPOSIT_SUCCESS".equals(command)) {
            log.info(data.toString());
            AuctionEventBus.fireEvent(AuctionEventBus.DEPOSIT_SUCCESS, data);
        }
    }
}