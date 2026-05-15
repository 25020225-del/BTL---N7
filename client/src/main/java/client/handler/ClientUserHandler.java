package client.handler;

import client.network.NetworkClient;
import javafx.application.Platform;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.net.URI;
import java.util.Map;

public class ClientUserHandler implements  ResponseHandler{
    private static final Logger log = LoggerFactory.getLogger(ClientUserHandler.class);

    @Override
    public void handle(NetworkMessage message, NetworkClient client) throws Exception {

        String command = message.getCommand();

        System.out.println("Command: " + command);
        AuctionEventBus.fireEvent(command,message);
    }
}
