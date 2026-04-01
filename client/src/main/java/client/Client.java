package client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class Client{
    private static final String API_TOKEN = "YODWeA21rcm9VMvqsugbpjnx0adwZf5PUGTSCHJBt6z8k";
    public static void main(String[] args){
        System.out.println("Getting server address from API...");
        String[] serverInfo=getServerAddress();
        if(serverInfo==null || serverInfo.length==0){
            System.err.println("Server address not found");
            return;
        }
        final String SERVER_IP=serverInfo[0];
        final int SERVER_PORT=Integer.parseInt(serverInfo[1]);
        try{
            Socket socket=new Socket(SERVER_IP, SERVER_PORT);
            PrintWriter out=new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Scanner scanner=new Scanner(System.in);
            // Thread for receiving messages from the server
            Thread receiveThread=new Thread(()->{
                try{
                    String serverMessage;
                    serverMessage=in.readLine();
                    System.out.println(serverMessage);
                    while((serverMessage=in.readLine())!= null){
                        System.out.println("\n" + serverMessage);
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
                if ("STOP".equalsIgnoreCase(messageToSend.trim())) {
                    System.out.println("Disconnecting in progress...");
                    socket.close();
                    break;
                }
                if ("OPEN AUCTION".trim().equalsIgnoreCase(messageToSend.trim())) {
                    ClientCallLauncher launcher=new ClientCallLauncher();
                    new Thread(launcher).start();
                }
            }
        }catch (IOException e){
            System.err.println("Cannot connect to the Server: " + e.getMessage());
        }
    }
    private static String[] getServerAddress() {
        try {
            // Getting tunnels
            URL url = new URL("https://localtonet.com/api/GetTunnels");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + API_TOKEN);
            conn.setRequestProperty("Accept", "application/json");

            // JSON data reader
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();

            String jsonResponse = content.toString();

            // XỬ LÝ CHUỖI JSON ĐỂ TÌM IP VÀ PORT (Đã cập nhật theo cấu trúc mới)
            String ip = "";
            String port = "";
            //IP
            Matcher ipMatcher = Pattern.compile("\"serverDomain\":\"([^\"]+)\"").matcher(jsonResponse);
            if (ipMatcher.find()) {
                ip = ipMatcher.group(1);
            }
            //PORT
            Matcher portMatcher = Pattern.compile("\"serverPort\":(\\d+)").matcher(jsonResponse);
            if (portMatcher.find()) {
                port = portMatcher.group(1);
            }

            if (!ip.isEmpty() && !port.isEmpty()) {
                return new String[]{ip, port};
            } else {
                // DEBUG
                System.out.println("API Data (Debug): " + jsonResponse);
            }

        } catch (Exception e) {
            System.out.println("API Error: " + e.getMessage());
        }
        return null; // null if error/undetected
    }
}
