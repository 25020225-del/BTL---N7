package client.handler;

import client.network.NetworkClient;
import network.NetworkMessage;
import javafx.application.Platform;
import gui.process.AlertHelper;
import javafx.scene.control.Alert.AlertType;

import static utils.ConsoleColors.*;

public class ClientAuctionHandler implements ResponseHandler {
    @Override
    public void handle(NetworkMessage message, NetworkClient client) {
        String command = message.getCommand();

        if ("CLI_BROADCAST".equals(command)) {
            System.out.println(YELLOW + message.getData().toString() + RESET);
        }
        else if ("CREATE_SUCCESS".equals(command)) {
            System.out.println("[System]: " + GREEN + message.getData() + RESET);
            Platform.runLater(() -> {
                AlertHelper.showAlert(AlertType.INFORMATION, "Success", message.getData().toString());
            });
        }
        else if ("CHAT".equals(command)) {
            System.out.println(message.getData());
        }
    }
}