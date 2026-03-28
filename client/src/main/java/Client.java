package client;
import java.io.*;
import java.net.Socket;
import java.util.Scanner;
public class Client {
    public static void main(String[] args) {
        final String SERVER_IP = "127.0.0.1";
        final int SERVER_PORT = 6969;

        try (
                // Kết nối
                Socket socket = new Socket(SERVER_IP, SERVER_PORT);

                // Mở luồng
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Đọc input
                Scanner scanner = new Scanner(System.in)
        ) {
            System.out.println("Connection is set. Type any messages. Type \"STOP\" to stop:");

            while (true) {
                System.out.print("You: ");
                String messageToSend = scanner.nextLine(); // Chờ bạn gõ chữ và nhấn Enter

                // Gửi input
                out.println(messageToSend);

                // Stop
                if ("STOP".equalsIgnoreCase(messageToSend)) {
                    System.out.println("Disconnecting in progress");
                    break;
                }

                // phản hồi từ Server
                String serverResponse = in.readLine();
                System.out.println("Server: " + serverResponse);
            }

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}