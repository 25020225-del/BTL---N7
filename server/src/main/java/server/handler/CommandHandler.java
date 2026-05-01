package server.handler;

import network.NetworkMessage;
import server.ClientHandler;

public interface CommandHandler {
    void handle(NetworkMessage message, ClientHandler client) throws Exception;
}