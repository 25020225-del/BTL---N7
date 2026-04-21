package server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import controller.UserController;
import model.User;
import network.NetworkMessage;

import java.io.*;
import java.net.Socket;

import static utils.ConsoleColors.*;

public class ClientHandler implements Runnable {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private int cNC = 0;
    private volatile String clientName = "Guest" + (cNC++);

    private UserController userController;

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ClientHandler(Socket socket, UserController userController) {
        this.socket = socket;
        this.userController = userController;

        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            System.out.println("[Error]: I/O exception in ClientHandler: " + RED + e.getMessage() + RESET);
        }
    }

    @Override
    public void run() {
        try {
            String jsonMessage;
            while ((jsonMessage = in.readLine()) != null) {
                System.out.println("[System]: Getting JSON from Client: " + YELLOW + jsonMessage + RESET);

                try {
                    NetworkMessage message = mapper.readValue(jsonMessage, NetworkMessage.class);
                    String command = message.getCommand();

                    if (command == null) {
                        System.out.println("[System]: \"" + YELLOW + clientName + RESET + "\" tried to send a null command");
                        sendResponse("ERROR", "Command cannot be null");
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

                        case "CREATE_AUCTION":
                            handleCreateAuction(message.getData());
                            break;

                        default:
                            System.out.println("[Error]: Unrecognized command: " + RED + command + RESET);
                            sendResponse("ERROR", "Unrecognized command");
                            break;
                    }
                } catch (Exception e) {
                    System.out.println("[Error]: Invalid JSON format: " + RED + e.getMessage() + RESET);
                    sendResponse("ERROR", "Invalid JSON format");
                }
            }
        } catch (IOException e) {
            System.out.println("[System]: Lost connection with " + (clientName != null ? clientName : "unknown Client"));
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
                String[] parts = result.split("\\|");
                String secretKey = parts[1];
                String qrUrl = parts[2];

                String[] responseData = {secretKey, qrUrl};

                sendResponse("REGISTER_SUCCESS", responseData);
                this.clientName = regUser.getUserName();
            } else {
                sendResponse("REGISTER_FAIL", result);
            }
        } catch (IllegalArgumentException e) {
            System.out.println("[Error]: Mapping JSON to User (Register): " + RED + e.getMessage() + RESET);
            sendResponse("ERROR", "Invalid register data");
        }
    }

    private void handleLogin(Object data) {
        try {
            User loginAttempt = mapper.convertValue(data, User.class);
            User user = userController.login(loginAttempt.getUserName(), loginAttempt.getUserPass());

            if (user != null) {
                clientName = user.getUserName();
                sendResponse("LOGIN_SUCCESS", user);
            } else {
                sendResponse("LOGIN_FAIL", "Wrong username or password");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("[Error]: Mapping JSON to User (Login): " + RED + e.getMessage() + RESET);
            sendResponse("ERROR", "Invalid login data");
        }
    }

    public void sendResponse(String command, Object data) {
        try {
            NetworkMessage responseMsg = new NetworkMessage(command, data);
            String jsonOutput = mapper.writeValueAsString(responseMsg);
            out.println(jsonOutput);
        } catch (Exception e) {
            System.out.println("[Error]: JSON serialization: " + RED + e.getMessage() + RESET);
        }
    }

    public void sendMessage(String message) {
        sendResponse("CHAT", message);
    }

    private void closeConnection() {
        MultiThreadedServer.removeClient(this);
        System.out.println("[System]: \"" + YELLOW + clientName + RESET + "\" has stopped connecting");
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.out.println("[Error]: Socket closing error: " + RED + e.getMessage() + RESET);
        }
    }

    public String getClientName() {
        return clientName;
    }

    public void forceDisconnect(String reason) {
        sendResponse("KICKED", reason);
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.out.println("[Error]: Socket closing error: " + RED + e.getMessage() + RESET);
        }
    }

    public void redirectToWebsite(String url) {
        sendResponse("REDIRECT", url);
    }

    private void handleCreateAuction(Object data) {
        try {
            java.util.Map<String, String> map = mapper.convertValue(data, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});

            String itemName = map.get("itemName");
            String description = map.get("description");
            double startingPrice = Double.parseDouble(map.get("startingPrice"));
            double bidIncrement = Double.parseDouble(map.get("bidIncrement"));
            int durationMinutes = Integer.parseInt(map.get("durationMinutes"));

            model.Item item = new model.Art("ITM-" + System.currentTimeMillis(), itemName, description, startingPrice);

            model.User currentSeller = new model.User("U-" + this.clientName, this.clientName, "", this.clientName, "SELLER");
            currentSeller.setGood(true);

            controller.ServerSellerController sellerCtrl = new controller.ServerSellerController();
            model.Auction newAuction = sellerCtrl.addAuction(currentSeller, item, bidIncrement, durationMinutes);

            if (newAuction != null) {
                newAuction.setStatus(model.Auction.STATUS_RUNNING);

                MultiThreadedServer.auctionList.add(newAuction);

                System.out.println("[System]: Seller \"" + YELLOW + this.clientName + RESET + "\" has created an auction");
                System.out.println("[System]: Item: " + YELLOW + itemName + RESET + " - Starting Price: " + YELLOW + startingPrice + " VND" + RESET);

                String alertMsg = "[System]: Seller \"" + this.clientName + "\" has created an auction of \"" + YELLOW + itemName + RESET + "\" with the price of " + YELLOW + startingPrice +RESET + " VND";
                MultiThreadedServer.broadcast("CLI_BROADCAST", alertMsg, this);

                sendResponse("CREATE_SUCCESS", "Successfully created auction");
            } else {
                sendResponse("ERROR", "Database Error when creating auction");
            }

        } catch (Exception e) {
            System.out.println("[Error]: " + "Error when trying CREATE_AUCTION: " + RED + e.getMessage() + RESET);
            sendResponse("ERROR", "Auction creation data is invalid");
        }
    }
}