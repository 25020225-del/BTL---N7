package server.ServerExtension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ClientHandler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static utils.ConsoleColors.*;

/**
 * Manages all active client connections and coordinates communication across the server.
 */
public class ClientManager {
    private static final Logger log = LoggerFactory.getLogger(ClientManager.class);

    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private static final int MAX_BROADCASTPOOL_SIZE = 200;
    private static final ExecutorService broadcastPool = Executors.newFixedThreadPool(MAX_BROADCASTPOOL_SIZE);

    public static void addClient(ClientHandler clientHandler) {
        clients.add(clientHandler);
    }

    public static void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
    }

    public static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                broadcastPool.submit(() -> {
                    try {
                        client.sendMessage(message);
                    } catch (Exception e) {
                        log.error("Error when broadcasting to {}: {}", client.getClientName(), e.getMessage());
                    }
                });
            }
        }
    }

    public static void broadcast(String command, Object data, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                broadcastPool.submit(() -> {
                    try {
                        client.sendResponse(command, data);
                    } catch (Exception e) {
                        log.error("Cannot broadcast to {}", client.getClientName());
                    }
                });
            }
        }
    }

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

    // [ARCHITECT FIX]: Thêm hàm hỗ trợ kick bằng ID cố định thay vì Username
    /**
     * Forcibly disconnects a client from the server by their User ID.
     * @param userId The unique ID of the client to be kicked.
     * @param reason The justification for the forced disconnection.
     */
    public static void kickTargetById(String userId, String reason) {
        ClientHandler targetToKick = null;
        for (ClientHandler client : clients) {
            if (client.getUser() != null && client.getUser().getId().equals(userId)) {
                targetToKick = client;
                break;
            }
        }
        if (targetToKick != null) {
            System.out.println("[System]: User ID \"" + YELLOW + userId + RESET + "\" has been kicked (Blocked by Admin)");
            targetToKick.forceDisconnect(reason);
        }
    }

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

    public static void redirectClient(String clientName, String url) {
        for (ClientHandler client : clients) {
            if (client.getClientName() != null && client.getClientName().equals(clientName)) {
                client.redirectToWebsite(url);
                return;
            }
        }
        System.out.println("[System]: User \"" + YELLOW + clientName + RESET + "\" doesn't exist");
    }

    public static void sendToUser(String userId, String command, Object data) {
        for (ClientHandler client : clients) {
            if (client.getUser() != null && client.getUser().getId().equals(userId)) {
                client.sendResponse(command, data);
                return;
            }
        }
    }

    public static void shutdown() {
        broadcastPool.shutdown();
    }
}