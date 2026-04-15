package server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import controller.UserController;
import model.User;
import network.NetworkMessage;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String clientName;

    private UserController userController;
    private User loggedInUser = null;

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ClientHandler(Socket socket, UserController userController) {
        this.socket = socket;
        this.userController = userController;

        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            System.out.println("[System]: A client has connected");

            String jsonMessage;
            while ((jsonMessage = in.readLine()) != null) {

                System.out.println("[System]: Getting JSON from Client: " + jsonMessage);

                try {
                    NetworkMessage message = mapper.readValue(jsonMessage, NetworkMessage.class);
                    String command = message.getCommand();

                    switch (command) {
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
                    System.err.println("[Error]: Invalid JSON format: " + e.getMessage());
                    sendResponse("ERROR", "Invalid JSON format");
                }
            }
        } catch (IOException e) {
            System.out.println("[System]: Lost connection with " + (clientName != null ? clientName : "unknown Client"));
        } finally {
            closeConnection();
        }
    }

    // ==========================================
    // XỬ LÝ LỆNH ĐĂNG KÝ VÀ ĐĂNG NHẬP
    // ==========================================

    private void handleRegister(Object data) {
        try {
            User regUser = mapper.convertValue(data, User.class);
            String result = userController.register(
                    regUser.getUserName(),
                    regUser.getUserPass(),
                    regUser.getName(),
                    regUser.getRole()
            );

            // --- ĐÃ SỬA: Bắt chuỗi SUCCESS chứa QR Link ---
            if (result != null && result.startsWith("SUCCESS|")) {
                // Cắt lấy phần đường link QR nằm ở phía sau dấu |
                String qrUrl = result.split("\\|")[1];

                // Gửi nguyên cái link QR này về làm data cho Client
                sendResponse("REGISTER_SUCCESS", qrUrl);
            } else {
                sendResponse("REGISTER_FAIL", result);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("[Error]: Mapping JSON to User (Register): " + e.getMessage());
            sendResponse("ERROR", "Invalid register data");
        }
    }

    private void handleLogin(Object data) {
        try {
            User loginAttempt = mapper.convertValue(data, User.class);
            User user = userController.login(loginAttempt.getUserName(), loginAttempt.getUserPass());

            if (user != null) {
                this.loggedInUser = user;
                this.clientName = user.getName();

                sendResponse("LOGIN_SUCCESS", user);
                MultiThreadedServer.broadcast("[System]: " + this.clientName + " has joined auction", this);
            } else {
                sendResponse("LOGIN_FAIL", "Wrong username or password");
            }
        } catch (IllegalArgumentException e) {
            System.err.println("[Error]: Mapping JSON to User (Login): " + e.getMessage());
            sendResponse("ERROR", "Invalid login data");
        }
    }

    // ==========================================
    // CÁC HÀM TIỆN ÍCH GỬI DỮ LIỆU
    // ==========================================

    public void sendResponse(String command, Object data) {
        try {
            NetworkMessage responseMsg = new NetworkMessage(command, data);
            String jsonOutput = mapper.writeValueAsString(responseMsg);
            out.println(jsonOutput);
        } catch (Exception e) {
            System.err.println("[Error]: JSON serialization: " + e.getMessage());
        }
    }

    public void sendMessage(String message) {
        sendResponse("CHAT", message);
    }

    private void closeConnection() {
        MultiThreadedServer.removeClient(this);
        if (clientName != null) {
            MultiThreadedServer.broadcast("[System]: " + clientName + " has stopped connecting", this);
        }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getClientName() { return this.clientName; }

    public void forceDisconnect(String reason) {
        sendResponse("KICKED", reason);
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void redirectToWebsite(String url) {
        sendResponse("REDIRECT", url);
    }
}