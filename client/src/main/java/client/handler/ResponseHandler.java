package client.handler;

import client.network.NetworkClient;
import network.NetworkMessage;

/**
 * Defines the execution contract for processing inbound network payloads from the server.
 * Implementations act as specialized command handlers within the client architecture,
 * encapsulating message decoding, state delegation, or safe UI thread dispatching.
 */
@FunctionalInterface
public interface ResponseHandler {

    /**
     * Processes an inbound server message and executes the associated domain or presentation logic.
     *
     * @param message the canonical network message containing the server command and payload
     * @param client  the active network client session context for upstream communication
     * @throws Exception if payload deserialization, state validation, or thread scheduling fails
     */
    void handle(NetworkMessage message, NetworkClient client) throws Exception;
}