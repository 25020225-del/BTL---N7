package client.handler;

import client.network.NetworkClient;
import network.NetworkMessage;

public interface ResponseHandler {
    void handle(NetworkMessage message, NetworkClient client) throws Exception;
}