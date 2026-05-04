package server.ServerExtension;

import server.ClientHandler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static utils.ConsoleColors.*;

/**
 * Manages all active client connections and coordinates communication across the server.
 * This class serves as the central registry for {@link ClientHandler} instances and
 * provides utility methods for broadcasting messages, private messaging, and
 * administrative actions such as kicking or redirecting clients.
 */
public class ClientManager {

    /**
     * A thread-safe list of all currently connected clients.
     * Uses {@link CopyOnWriteArrayList} to ensure safe iteration during broadcasting
     * while clients may be connecting or disconnecting.
     */
    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    /**
     * The maximum number of threads dedicated to processing broadcast tasks.
     */
    private static final int MAX_BROADCASTPOOL_SIZE = 200;

    /**
     * Executor service dedicated to asynchronous broadcasting.
     * This prevents a single slow client from blocking the delivery of messages
     * to other participants in the system.
     */
    private static final ExecutorService broadcastPool = Executors.newFixedThreadPool(MAX_BROADCASTPOOL_SIZE);

    /**
     * Registers a new client connection in the global registry.
     *
     * @param clientHandler The handler for the newly connected client.
     */
    public static void addClient(ClientHandler clientHandler) {
        clients.add(clientHandler);
    }

    /**
     * Removes a client connection from the registry.
     *
     * @param clientHandler The handler to be removed.
     */
    public static void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
    }

    /**
     * Broadcasts a plain text message to all connected clients except the sender.
     * Delivery is handled asynchronously via the broadcast thread pool.
     *
     * @param message The text content to be sent.
     * @param sender  The client handler that initiated the broadcast (excluded from receiving).
     */
    public static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                broadcastPool.submit(() -> {
                    try {
                        client.sendMessage(message);
                    } catch (Exception e) {
                        System.out.println("[Error]: Error when trying to broadcast to \"" +
                                YELLOW + client.getClientName() + RESET + "\": " +
                                RED + e.getMessage() + RESET);
                    }
                });
            }
        }
    }

    /**
     * Broadcasts a command-based network message to all connected clients except the sender.
     * This is typically used for real-time UI updates across the system.
     *
     * @param command The identifier for the action the clients should perform.
     * @param data    The data payload associated with the command.
     * @param sender  The client handler that initiated the broadcast (excluded from receiving).
     */
    public static void broadcast(String command, Object data, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                broadcastPool.submit(() -> {
                    try {
                        client.sendResponse(command, data);
                    } catch (Exception e) {
                        System.out.println("[Error]: Broadcasting error to \"" + YELLOW + client.getClientName() + RESET + "\"");
                    }
                });
            }
        }
    }

    /**
     * Sends a private administrative message to a specific user by their name.
     *
     * @param receiver The username of the target recipient.
     * @param message  The content of the private message.
     */
    public static void privateMsg(String receiver, String message) {
        receiver = receiver.trim();
        for (ClientHandler client : clients) {
            if (client.getClientName() != null && client.getClientName().equals(receiver)) {
                client.sendMessage("[Admin]" + BLUE + "(private)" + RESET + ": " + message);
                return;
            }
        }
        System.out.println("[System]: User \"" + receiver + "\" doesn't exist");
    }

    /**
     * Forcibly disconnects a client from the server by their username.
     *
     * @param target The username of the client to be kicked.
     * @param reason The justification for the forced disconnection.
     */
    public static void kickTarget(String target, String reason) {
        ClientHandler targetToKick = null;
        for (ClientHandler client : clients) {
            if (client.getClientName() != null && client.getClientName().equalsIgnoreCase(target)) {
                targetToKick = client;
                break;
            }
        }
        if (targetToKick != null) {
            System.out.println("[System]: \"" + YELLOW + target + RESET + "\" has been kicked");
            targetToKick.forceDisconnect(reason);
        } else {
            System.out.println("[System]: User \"" + YELLOW + target + RESET + "\" doesn't exist");
        }
    }

    /**
     * Forcibly disconnects a client based on their index in the internal list.
     * Primarily used for administrative console commands.
     *
     * @param i      The index of the client in the registry.
     * @param reason The justification for the forced disconnection.
     */
    public static void kickTargetByNumber(int i, String reason) {
        ClientHandler targetToKick = null;
        if (i >= 0 && i < clients.size()) {
            targetToKick = clients.get(i);
        }

        if (targetToKick != null) {
            System.out.println("[System]: \"" + YELLOW + targetToKick.getClientName() + RESET + "\" has been kicked");
            targetToKick.forceDisconnect(reason);
        } else {
            System.out.println("[System]: Client index " + i + " doesn't exist");
        }
    }

    /**
     * Prints a formatted list of all currently connected clients to the server console.
     */
    public static void getClientList() {
        int count = 0;
        if (clients.isEmpty()) {
            System.out.println("[System]: There are no clients connected");
        } else {
            System.out.println(GREEN + "=======================" + RESET);
            for (ClientHandler client : clients) {
                System.out.println(count + ". " + client.getClientName());
                count++;
            }
            System.out.println("Total: " + count + " client(s)");
            System.out.println(GREEN + "=======================" + RESET);
        }
    }

    /**
     * Instructs a specific client to open a website URL.
     * Used for redirecting users to external services such as PayPal for transactions.
     *
     * @param clientName The name of the client to redirect.
     * @param url        The external URL to be opened.
     */
    public static void redirectClient(String clientName, String url) {
        for (ClientHandler client : clients) {
            if (client.getClientName() != null && client.getClientName().equals(clientName)) {
                client.redirectToWebsite(url);
                return;
            }
        }
        System.out.println("[System]: User \"" + YELLOW + clientName + RESET + "\" doesn't exist");
    }

    /**
     * Gracefully shuts down the broadcast thread pool.
     */
    public static void shutdown() {
        broadcastPool.shutdown();
    }
}