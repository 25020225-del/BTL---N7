package client.handler;

import client.network.NetworkClient;
import network.NetworkMessage;

/**
 * Represents a contract for handling specific types of network responses from the server.
 * Implementations of this interface define the logic for processing incoming
 * {@link NetworkMessage} commands (e.g., updating UI components, managing application state,
 * or routing follow-up requests).
 */
public interface ResponseHandler {

    /**
     * Processes the incoming network message received from the server.
     *
     * @param message The network message containing the server's command and payload data.
     * @param client  The active network client session, allowing handlers to send back
     *                follow-up requests to the server if necessary.
     * @throws Exception If an error occurs during message parsing, data casting, or UI thread updates.
     */
    void handle(NetworkMessage message, NetworkClient client) throws Exception;
}