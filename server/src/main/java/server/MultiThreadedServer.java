package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MultiThreadedServer {
    // Danh sách lưu trữ các nhân viên (ClientHandler) đang phục vụ các Client
    private static final List<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args) {
        final int PORT = 6969;

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("MultiThreaded Server đang chạy trên cổng " + PORT + "...");

            // Vòng lặp vô tận để liên tục đón các Client mới
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Có một Client mới kết nối từ: " + socket.getInetAddress().getHostAddress());

                // Tạo một ClientHandler riêng để phục vụ Client này
                ClientHandler clientHandler = new ClientHandler(socket);

                // Thêm vào danh sách quản lý chung
                clients.add(clientHandler);

                // Khởi chạy luồng (Thread) cho ClientHandler này hoạt động
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Lỗi Server: " + e.getMessage());
        }
    }

    // Phương thức dùng chung để gửi tin nhắn đến TẤT CẢ mọi người (trừ người gửi)
    public static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) { // Không gửi ngược lại cho chính người vừa nhắn
                client.sendMessage(message);
            }
        }
    }

    // Xóa Client khỏi danh sách khi họ ngắt kết nối
    public static void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
    }
}