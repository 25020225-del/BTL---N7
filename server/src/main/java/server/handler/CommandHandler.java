package server.handler;

import network.NetworkMessage;
import server.ClientHandler;

/**
 * Functional interface defining the contract for processing specific network commands.
 */
@FunctionalInterface
public interface CommandHandler {

    /**
     * Executes the domain business logic associated with a specific ingress command.
     *
     * @param message the incoming network message context container
     * @param client  the stateful client connection session handler
     * @throws Exception if any unrecoverable structural or business processing failure occurs
     */
    void handle(NetworkMessage message, ClientHandler client) throws Exception;
}