package client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        final String SERVER_IP = "10.11.205.75";
        final int SERVER_PORT = 6969;

        try {
            Socket socket = new Socket(SERVER_IP, SERVER_PORT);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Scanner scanner = new Scanner(System.in);

            // =======================================================
            // LUỒNG CHUYÊN NHẬN TIN NHẮN TỪ PHÒNG CHAT (TỪ SERVER)
            // =======================================================
            Thread receiveThread = new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        System.out.println("\n" + serverMessage);
                        System.out.print("Bạn: "); // In lại dấu nhắc gõ phím
                    }
                } catch (IOException e) {
                    System.out.println("\nĐã ngắt kết nối với Server.");
                }
            });
            receiveThread.start();

            // =======================================================
            // LUỒNG CHÍNH CHUYÊN GÕ VÀ GỬI TIN NHẮN
            // =======================================================
            while (true) {
                String messageToSend = scanner.nextLine();

                // Gửi lên Server (cho ClientHandler xử lý)
                out.println(messageToSend);

                if ("STOP".equalsIgnoreCase(messageToSend)) {
                    System.out.println("Đang thoát khỏi phòng chat...");
                    socket.close();
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Không thể kết nối đến Server: " + e.getMessage());
        }
    }
}