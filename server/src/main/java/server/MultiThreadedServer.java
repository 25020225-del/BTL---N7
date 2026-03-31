package server;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
public class MultiThreadedServer {
    private static final List<ClientHandler> clients = new ArrayList<>();
    public static void main(String[] args) {
        final int PORT = 6969;
        //Thread allowing the Server Admin to type and send messages to all clients
        Thread serverChatThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String serverMessage = scanner.nextLine();
                // Send the server's message to everyone. Pass 'null' because the server is the sender.
                broadcast("[Admin]: " + serverMessage, null);
            }
        });
        serverChatThread.start();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("MultiThreaded Server is running on port " + PORT + "...");
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
            if (client != sender) {
                client.sendMessage(message);
            }
        }
    }
    public static void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
    }
}