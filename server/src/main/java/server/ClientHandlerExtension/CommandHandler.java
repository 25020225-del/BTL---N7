package server.ClientHandlerExtension;

import network.NetworkMessage;
import server.ClientHandler;

public interface CommandHandler {
    void handle(NetworkMessage message, ClientHandler client) throws Exception;
}