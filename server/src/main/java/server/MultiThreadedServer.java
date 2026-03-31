/*
Test code tạo luồng
*/
package server;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
public class MultiThreadedServer {
    private static final int PORT = 6969;
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is running at port: " + PORT);
            System.out.println("Waiting for connection");
            // Vòng lặp chờ client
            while (true) {
                // Chấp nhận kết nối
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client has connected: " + clientSocket.getInetAddress().getHostAddress());
                // Tạo luồng
                server.ClientHandler clientHandler = new server.ClientHandler(clientSocket);
                Thread thread = new Thread(clientHandler);
                thread.start();
            }
        } catch (IOException e) {System.err.println("Error: "+e.getMessage());}
    }
}