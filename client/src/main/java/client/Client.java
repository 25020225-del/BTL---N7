package client;
import java.io.*;
import java.net.Socket;
import java.util.Scanner;
public class Client{
    public static void main(String[] args){
        final String SERVER_IP="10.11.205.75";
        final int SERVER_PORT = 6969;
        try {
            Socket socket=new Socket(SERVER_IP, SERVER_PORT);
            PrintWriter out=new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Scanner scanner=new Scanner(System.in);
            // Thread for receiving messages from the server
            Thread receiveThread=new Thread(()->{
                try{
                    String serverMessage;
                    while((serverMessage=in.readLine())!= null){
                        System.out.println("\n" + serverMessage);
                        System.out.print("[You]: ");
                    }
                }catch(IOException e){
                    System.out.println("\nDisconnected from the Server.");
                }
            });
            receiveThread.start();
            // Main thread for sending messages
            System.out.println("Connected to the Server. Type \"STOP\" to stop.");
            try{Thread.sleep(100);}catch(InterruptedException e){e.printStackTrace();}
            while(true){
                String messageToSend=scanner.nextLine();
                out.println(messageToSend);
                if ("STOP".equalsIgnoreCase(messageToSend)) {
                    System.out.println("Disconnecting in progress...");
                    socket.close();
                    break;
                }
            }
        }catch (IOException e){
            System.err.println("Cannot connect to the Server: " + e.getMessage());
        }
    }
}