package server.handler;

import network.NetworkMessage;
import server.ClientHandler;

/**
 * Functional interface defining the contract for processing specific network commands.
 * Each implementation of this interface is responsible for handling a particular
 * action (e.g., Login, Bid, Create Auction) by interpreting the provided
 * {@link NetworkMessage} and interacting with the {@link ClientHandler}.
 */
public interface CommandHandler {

    /**
     * Executes the business logic associated with a specific command.
     *
     * @param message The incoming network message containing the command and data payload.
     * @param client  The client handler representing the active session and communication channel.
     * @throws Exception If an error occurs during command processing;
     *                   typically caught and handled by the {@link CommandDispatcher}.
     */
    void handle(NetworkMessage message, ClientHandler client) throws Exception;
}