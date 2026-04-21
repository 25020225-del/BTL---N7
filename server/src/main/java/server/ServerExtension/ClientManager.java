package server.ServerExtension;

import server.ClientHandler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static utils.ConsoleColors.*;

public class ClientManager {
    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private static final ExecutorService broadcastPool = Executors.newFixedThreadPool(20);

    public static void addClient(ClientHandler clientHandler) {
        clients.add(clientHandler);
    }

    public static void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
    }

    // Regular text
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

    // Command
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

    public static void privateMsg(String receiver, String message) {
        receiver = receiver.trim();
        for (ClientHandler client : clients) {
            if (client.getClientName() != null && client.getClientName().equals(receiver)) {
                client.sendMessage("[Admin]" + BLUE + " (private)" + RESET + ": " + message);
                return;
            }
        }
        System.out.println("[System]: User \"" + receiver + "\" doesn't exist");
    }

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

    public static void shutdown() {
        broadcastPool.shutdown();
    }
}