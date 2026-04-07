package server;

import controller.AuctionMonitor;
import model.Auction;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class MultiThreadedServer {
    private static final List<ClientHandler> clients = new ArrayList<>();

    // 1. THÊM DANH SÁCH ĐẤU GIÁ CHUNG CỦA TOÀN HỆ THỐNG
    public static final List<Auction> AUCTION_LIST = new ArrayList<>();

    public static void main(String[] args) {
        final int PORT = 6969;
        database.DatabaseManager.initializeDatabase();
        // 2. KHỞI TẠO VÀ BẬT HỆ THỐNG GIÁM SÁT THỜI GIAN
        AuctionMonitor monitor = new AuctionMonitor(AUCTION_LIST);
        monitor.startMonitoring();

        // ShutdownHook của bạn (Đã thêm lệnh tắt monitor)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            broadcast("[System]: Server is being closed. Every connecting client will be disconnected in a moment", null);
            broadcast("Server has been shutdown", null);

            // 3. Tắt monitor an toàn khi tắt Server
            monitor.stopMonitoring();
        }));

        // Thread allowing the Server Admin to type and send messages to all clients
        Thread serverChatThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                if (scanner.hasNextLine()) {
                    String serverMessage = scanner.nextLine();
                    if (serverMessage.startsWith("/kick ")) {
                        String target = serverMessage.substring(6);
                        System.out.println("Reason: ");
                        String reason = scanner.nextLine();
                        kickTarget(target, reason);
                    }
                    broadcast("[Admin]: " + serverMessage, null);
                }
            }
        });
        serverChatThread.start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is running on port " + PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected from: " + socket.getInetAddress().getHostAddress());
                ClientHandler clientHandler = new ClientHandler(socket);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Server Error: " + e.getMessage());
        }
    }

    // Broadcasts a message to all connected clients except the sender
    public static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) client.sendMessage(message);
        }
    }

    public static void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
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
            System.out.println(target + " has been kicked");
            targetToKick.forceDisconnect(reason);
        } else {
            System.out.println("ID \"" + target + "\" doesn't exist");
        }
    }
}