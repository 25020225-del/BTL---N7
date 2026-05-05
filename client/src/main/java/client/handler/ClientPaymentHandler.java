package client.handler;

import client.network.NetworkClient;
import gui.process.AlertHelper;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.net.URI;
import java.util.Map;

import static utils.ConsoleColors.*;

public class ClientPaymentHandler implements ResponseHandler {
    private static final Logger log =  LoggerFactory.getLogger(ClientPaymentHandler.class);

    @Override
    public void handle(NetworkMessage message, NetworkClient client) throws Exception {
        String command = message.getCommand();
        Object data = message.getData();

        if ("PAYMENT_REDIRECT".equals(command)) {
            // The server returns the order ID and the PayPal payment URL
            Map<String, String> responseData = (Map<String, String>) data;
            String url = responseData.get("url");
            String orderId = responseData.get("orderId");

            log.info("Open {} to complete payment.", url);

            // Open the browser automatically
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }

            // Display a confirmation message after payment is completed on the website
            Platform.runLater(() -> {
                Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                confirmAlert.setTitle("Payment Confirmation");
                confirmAlert.setHeaderText("Have you completed your payment via PayPal?");
                confirmAlert.setContentText("Order ID: " + orderId + "\nClick OK to update your balance.");

                confirmAlert.showAndWait().ifPresent(response -> {
                    if (response == javafx.scene.control.ButtonType.OK) {
                        // Send the CONFIRM_DEPOSIT command to the server
                        client.sendMessage("CONFIRM_DEPOSIT", orderId);
                    }
                });
            });
        } else if ("DEPOSIT_SUCCESS".equals(command)) {
            log.info(data.toString());
            Platform.runLater(() -> {
                AlertHelper.showAlert(Alert.AlertType.INFORMATION, "Success", data.toString());
            });
        }
    }
}