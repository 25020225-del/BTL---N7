package client.handler;

import client.network.NetworkClient;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import gui.process.AlertHelper;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * Central dispatcher that routes incoming JSON commands from the server
 * to their appropriate dedicated handler classes.
 */
public class ResponseDispatcher {
    private static final Logger log = LoggerFactory.getLogger(ResponseDispatcher.class);
    private final Map<String, ResponseHandler> handlers = new HashMap<>();

    public ResponseDispatcher() {
        registerHandlers();
    }

    private void registerHandlers() {
        ClientSystemHandler systemHandler = new ClientSystemHandler();
        handlers.put("REDIRECT", systemHandler);
        handlers.put("KICKED", systemHandler);
        handlers.put("TIME_SYNC_ACK", systemHandler);
        handlers.put("GENERAL_ERROR", systemHandler);

        ClientAuctionHandler auctionHandler = new ClientAuctionHandler();
        handlers.put("CLI_BROADCAST", auctionHandler);
        handlers.put("CREATE_SUCCESS", auctionHandler);
        handlers.put("CHAT", auctionHandler);
        handlers.put("UPDATE_AUCTION_PRICE", auctionHandler);

        ClientPaymentHandler paymentHandler = new ClientPaymentHandler();
        handlers.put("PAYMENT_REDIRECT", paymentHandler);
        handlers.put("DEPOSIT_SUCCESS", paymentHandler);

        ClientUserHandler userHandler = new ClientUserHandler();
        handlers.put("FETCH_AUCTIONS_SUCCESS", userHandler);
        handlers.put("FETCH_TRANSACTIONS_SUCCESS", userHandler);
        handlers.put("NEW_AUCTION_ADDED", userHandler);
        handlers.put("REMOVE_AUCTION", userHandler);
        handlers.put("EDIT_SUCCESS", userHandler);
        handlers.put("DELETE_SUCCESS", userHandler);
        handlers.put("FETCH_WALLET_SUCCESS", userHandler);
        handlers.put("FETCH_USERS_SUCCESS", userHandler);
    }

    public void dispatch(NetworkMessage message, NetworkClient client) {
        String command = message.getCommand();
        ResponseHandler handler = handlers.get(command);

        System.out.println(command);

        if (handler != null) {
            try {
                handler.handle(message, client);
            } catch (Exception e) {
                // 1. Vẫn giữ nguyên dòng log cho Developer debug
                log.error("Command \"{}\": {}", command, e.getMessage());
                e.printStackTrace();

                // 2. [FIXED]: Thiết lập lưới an toàn (Catch-all) bắn Pop-up báo lỗi trực quan cho End-user
                Platform.runLater(() -> {
                    AlertHelper.showAlert(
                            Alert.AlertType.ERROR,
                            "Lỗi hệ thống",
                            "Lỗi xử lý dữ liệu cục bộ: " + e.getMessage()
                    );
                });
            }
        } else {
            // Unhandled commands are forwarded directly to the UI component if a callback is registered
            if (client.getOnMessageReceived() != null) {
                Platform.runLater(() -> client.getOnMessageReceived().accept(message));
            }
        }
    }
}