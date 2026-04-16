package server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import controller.UserController;
import model.User;
import network.NetworkMessage;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    public static final String ANSI_RESET  = "\u001B[0m";
    public static final String ANSI_RED    = "\u001B[31m";
    public static final String ANSI_GREEN  = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE   = "\u001B[34m";

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private int cNC=0;
    private volatile String clientName="Guest"+String.valueOf(cNC++);

    private UserController userController;

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);

    public ClientHandler(Socket socket,UserController userController){
        this.socket = socket;
        this.userController = userController;

        try {
            this.in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(),true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run(){
        try{
            String jsonMessage;
            while((jsonMessage=in.readLine())!=null){

                System.out.println("[System]: Getting JSON from Client: "+ANSI_YELLOW+jsonMessage+ANSI_RESET);

                try {
                    NetworkMessage message = mapper.readValue(jsonMessage,NetworkMessage.class);
                    String command = message.getCommand();

                    if(command==null){
                        System.out.println("[System]: \""+ANSI_YELLOW+clientName+ANSI_RESET+"\" tried to send a null command");
                        sendResponse("ERROR","Command cannot be null");
                        continue;
                    }

                    switch (command) {
                        case "PING":
                            sendResponse("PONG", "Request accepted");
                            break;

                        case "REGISTER":
                            handleRegister(message.getData());
                            break;

                        case "LOGIN":
                            handleLogin(message.getData());
                            break;

                        default:
                            System.out.println("[Error]: Unrecognized command: " + command);
                            sendResponse("ERROR", "Unrecognized command");
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("[Error]: Invalid JSON format: "+ANSI_RED+e.getMessage()+ANSI_RESET);
                    sendResponse("ERROR", "Invalid JSON format");
                }
            }
        } catch (IOException e) {
            System.out.println("[System]: Lost connection with "+(clientName!=null?clientName: "unknown Client"));
        } finally {
            closeConnection();
        }
    }

    private void handleRegister(Object data) {
        try {
            User regUser = mapper.convertValue(data, User.class);
            String result = userController.register(
                    regUser.getUserName(),
                    regUser.getUserPass(),
                    regUser.getName(),
                    regUser.getRole()
            );

            if (result != null && result.startsWith("SUCCESS|")) {

                String qrUrl = result.split("\\|")[1];

                sendResponse("REGISTER_SUCCESS",qrUrl);
                this.clientName = regUser.getUserName();
            } else {
                sendResponse("REGISTER_FAIL",result);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("[Error]: Mapping JSON to User (Register): " + e.getMessage());
            sendResponse("ERROR","Invalid register data");
        }
    }

    private void handleLogin(Object data) {
        try {
            User loginAttempt = mapper.convertValue(data, User.class);
            User user = userController.login(loginAttempt.getUserName(), loginAttempt.getUserPass());

            if (user != null) {
                clientName = user.getUserName();
                sendResponse("LOGIN_SUCCESS",user);
            } else {
                sendResponse("LOGIN_FAIL", "Wrong username or password");
            }
        } catch (IllegalArgumentException e) {
            System.err.println("[Error]: Mapping JSON to User (Login): "+ANSI_RED+e.getMessage()+ANSI_RESET);
            sendResponse("ERROR", "Invalid login data");
        }
    }

    public void sendResponse(String command, Object data) {
        try {
            NetworkMessage responseMsg = new NetworkMessage(command, data);
            String jsonOutput = mapper.writeValueAsString(responseMsg);
            out.println(jsonOutput);
        } catch (Exception e) {
            System.err.println("[Error]: JSON serialization: "+ANSI_RED+e.getMessage()+ANSI_RESET);
        }
    }

    public void sendMessage(String message){sendResponse("CHAT",message);}

    private void closeConnection() {
        MultiThreadedServer.removeClient(this);
        System.out.println("[System]: \""+ANSI_YELLOW+clientName+ANSI_RESET+"\" has stopped connecting");
        try {
            if(socket!=null&&!socket.isClosed()) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getClientName(){return clientName;}

    public void forceDisconnect(String reason) {
        sendResponse("KICKED",reason);
        try {
            if(socket!=null&&!socket.isClosed()) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void redirectToWebsite(String url){sendResponse("REDIRECT", url);}
}