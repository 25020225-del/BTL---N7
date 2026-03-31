package client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        final String SERVER_IP = "10.11.205.0";
        final int SERVER_PORT = 6969;

        try {
            // Kết nối
            Socket socket = new Socket(SERVER_IP, SERVER_PORT);

            // Mở luồng
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Scanner scanner = new Scanner(System.in);

            System.out.println("Connection is set. Type any messages. Type \"STOP\" to stop:");

            // TẠO LUỒNG RIÊNG ĐỂ NHẬN TIN NHẮN TỪ SERVER (Sử dụng Lambda expression)
            Thread receiveThread = new Thread(() -> {
                try {
                    String serverResponse;
                    // Vòng lặp liên tục chờ đọc tin nhắn từ Server
                    while ((serverResponse = in.readLine()) != null) {
                        // Xóa dòng "You: " hiện tại, in tin nhắn server, rồi in lại "You: "
                        System.out.println("\nServer: " + serverResponse);
                        System.out.print("You: ");
                    }
                } catch (IOException e) {
                    System.out.println("\nĐã đóng luồng nhận tin nhắn từ Server.");
                }
            });
            receiveThread.start(); // Bắt đầu chạy luồng nhận tin

            // LUỒNG CHÍNH ĐỂ GỬI TIN NHẮN
            while (true) {
                System.out.print("You: ");
                String messageToSend = scanner.nextLine();

                // Gửi input
                out.println(messageToSend);

                // Stop
                if ("STOP".equalsIgnoreCase(messageToSend)) {
                    System.out.println("Disconnecting in progress...");
                    socket.close(); // Đóng socket sẽ làm `in.readLine()` ở luồng kia văng Exception và dừng lại
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}