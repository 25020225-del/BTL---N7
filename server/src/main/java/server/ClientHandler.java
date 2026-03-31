package server;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String clientName;

    // Constructor nhận vào Socket của Client vừa kết nối
    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            // Hỏi tên Client khi mới vào phòng
            out.println("Hệ thống: Vui lòng nhập tên của bạn để tham gia phòng chat:");
            this.clientName = in.readLine();

            // Thông báo cho toàn bộ phòng biết có người mới vào
            System.out.println(clientName + " đã tham gia phòng chat.");
            MultiThreadedServer.broadcast("Hệ thống: " + clientName + " đã tham gia phòng chat!", this);

            String message;
            // Liên tục lắng nghe tin nhắn từ Client này
            while ((message = in.readLine()) != null) {
                if ("STOP".equalsIgnoreCase(message)) {
                    break;
                }
                // Nhận được tin nhắn thì phát (broadcast) cho tất cả các Client khác
                MultiThreadedServer.broadcast(clientName + ": " + message, this);
            }
        } catch (IOException e) {
            System.out.println("Lỗi đột ngột ngắt kết nối với " + clientName);
        } finally {
            closeConnection();
        }
    }

    // Phương thức gửi tin nhắn NGƯỢC VỀ cho chính Client này
    public void sendMessage(String message) {
        out.println(message);
    }

    // Dọn dẹp khi Client thoát
    private void closeConnection() {
        MultiThreadedServer.removeClient(this);
        MultiThreadedServer.broadcast("Hệ thống: " + clientName + " đã rời phòng.", this);
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}