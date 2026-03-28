package server;
/*
Test code tạo client
*/
import java.io.*;
import java.net.Socket;
public class ClientHandler implements Runnable {
    private Socket clientSocket;
    public ClientHandler(Socket s){clientSocket=s;}
    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String clientMessage;

            // Nhận
            while ((clientMessage = in.readLine()) != null) {
                System.out.println("Receiving " + clientSocket.getPort() + ": " + clientMessage);

                // Phản hồi
                out.println("Received: " + clientMessage);

                // Dừng kết nối
                if ("STOP".equalsIgnoreCase(clientMessage)) {
                    System.out.println("Client " + clientSocket.getPort() + " has stopped connecting");
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {e.printStackTrace();}
        }
    }
}