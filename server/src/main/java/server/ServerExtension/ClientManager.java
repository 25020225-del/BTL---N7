package server.ServerExtension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ClientHandler;
import model.user.User;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static utils.ConsoleColors.*;

/**
 * Manages all active client connections and coordinates communication across the server.
 */
public class ClientManager {

    private static final Logger log = LoggerFactory.getLogger(ClientManager.class);

    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private static final int MAX_BROADCASTPOOL_SIZE = 200;
    private static final ExecutorService broadcastPool =
            Executors.newFixedThreadPool(MAX_BROADCASTPOOL_SIZE);

    // ─────────────────────────────────────────────────────────────────────────
    // CLIENT REGISTRY
    // ─────────────────────────────────────────────────────────────────────────

    public static void addClient(ClientHandler clientHandler) {
        clients.add(clientHandler);
    }

    public static void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BROADCAST
    // ─────────────────────────────────────────────────────────────────────────

    /** Gửi plain-text chat đến tất cả client (ngoại trừ sender). */
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

    /** Gửi NetworkMessage (command + data) đến tất cả client (ngoại trừ sender). */
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

    // ─────────────────────────────────────────────────────────────────────────
    // TARGETED MESSAGING
    // ─────────────────────────────────────────────────────────────────────────

    /** Gửi private message từ Admin đến một client theo username. */
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
     * Gửi một response có mục tiêu đến một user cụ thể theo userId.
     * Thread-safe: dùng broadcastPool để không block DB Worker Thread.
     * Silent-miss policy: bỏ qua nếu user offline.
     */
    public static void sendToUser(String userId, String command, Object data) {
        for (ClientHandler client : clients) {
            User user = client.getUser();
            if (user != null && user.getId().equals(userId)) {
                broadcastPool.submit(() -> {
                    try {
                        client.sendResponse(command, data);
                        log.debug("Push notification sent to user {}: command={}", userId, command);
                    } catch (Exception e) {
                        log.error("Failed to send notification to user {}: {}", userId, e.getMessage());
                    }
                });
                return;
            }
        }
        log.debug("sendToUser: user {} is offline. Notification '{}' dropped.", userId, command);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADMIN CONSOLE COMMANDS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * [FIX] Kick client theo username.
     * Sửa lỗi: dùng client.forceDisconnect() thay vì client.getSocket().close()
     * (ClientHandler không expose getSocket()).
     */
    public static void kickTarget(String target, String reason) {
        for (ClientHandler client : clients) {
            if (target.equals(client.getClientName())) {
                client.forceDisconnect(reason); // forceDisconnect gửi KICKED rồi đóng kết nối
                log.info("kickTarget: User '{}' has been kicked. Reason: {}", target, reason);
                return;
            }
        }
        log.warn("kickTarget: User '{}' not found.", target);
    }

    /**
     * [NEW] Kick client theo số thứ tự trong danh sách (dùng cho lệnh /kickn).
     *
     * @param index  Vị trí (1-based) trong danh sách client.
     * @param reason Lý do kick.
     */
    public static void kickTargetByNumber(int index, String reason) {
        // Chuyển sang 0-based index
        int zeroBasedIndex = index - 1;
        if (zeroBasedIndex < 0 || zeroBasedIndex >= clients.size()) {
            log.warn("kickTargetByNumber: Index {} out of range (total clients: {}).", index, clients.size());
            System.out.println("[System]: Invalid client index: " + index);
            return;
        }
        ClientHandler target = clients.get(zeroBasedIndex);
        target.forceDisconnect(reason);
        log.info("kickTargetByNumber: Client #{} ('{}') kicked. Reason: {}", index, target.getClientName(), reason);
    }

    /**
     * [NEW] In ra console danh sách tất cả client đang kết nối (dùng cho lệnh /clist).
     */
    public static void getClientList() {
        if (clients.isEmpty()) {
            System.out.println("[System]: No clients connected.");
            return;
        }
        System.out.println("=== Connected Clients (" + clients.size() + ") ===");
        for (int i = 0; i < clients.size(); i++) {
            ClientHandler client = clients.get(i);
            User user = client.getUser();
            String identity = (user != null) ? user.getUserName() : "(not logged in)";
            System.out.printf("  [%d] Name: %-20s | Identity: %s%n",
                    i + 1, client.getClientName(), identity);
        }
        System.out.println("==========================================");
    }

    /**
     * [NEW] Redirect một client cụ thể đến một URL (dùng cho lệnh /redirect).
     *
     * @param target Username của client cần redirect.
     * @param url    URL đích.
     */
    public static void redirectClient(String target, String url) {
        for (ClientHandler client : clients) {
            if (target.equals(client.getClientName())) {
                client.redirectToWebsite(url);
                log.info("redirectClient: User '{}' redirected to '{}'.", target, url);
                return;
            }
        }
        log.warn("redirectClient: User '{}' not found.", target);
        System.out.println("[System]: User \"" + target + "\" not found for redirect.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * [NEW] Gracefully shuts down the broadcast thread pool.
     * Gọi trong Shutdown Hook của MultiThreadedServer.
     */
    public static void shutdown() {
        log.info("ClientManager: Shutting down broadcast pool...");
        broadcastPool.shutdown();
        try {
            if (!broadcastPool.awaitTermination(5, TimeUnit.SECONDS)) {
                broadcastPool.shutdownNow();
                log.warn("ClientManager: Broadcast pool forced shutdown after timeout.");
            }
        } catch (InterruptedException e) {
            broadcastPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("ClientManager: Broadcast pool shut down.");
    }
}