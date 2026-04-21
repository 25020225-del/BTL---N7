package client;

import network.NetworkMessage;

public interface ResponseHandler {
    void handle(NetworkMessage message, NetworkClient client) throws Exception;
}