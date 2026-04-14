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

    // --- ĐÃ SỬA CHỖ NÀY: Dạy cho Jackson biết cách "bơ" đi những biến thừa ---
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
            System.out.println("Một Client vô danh vừa kết nối.");

            String jsonMessage;
            while ((jsonMessage = in.readLine()) != null) {

                if ("STOP".equalsIgnoreCase(jsonMessage.trim()) || "QUIT".equalsIgnoreCase(jsonMessage.trim())) {
                    System.out.println((clientName != null ? clientName : "Client vô danh") + " đã chủ động ngắt kết nối.");
                    break;
                }

                System.out.println("Nhận JSON từ Client: " + jsonMessage);

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
                            System.out.println("Nhận lệnh không xác định: " + command);
                            sendResponse("ERROR", "Unrecognized command");
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("Lỗi phân tích JSON: " + e.getMessage());
                    sendResponse("ERROR", "Invalid JSON format");
                }
            }
        } catch (IOException e) {
            System.out.println("Mất kết nối với " + (clientName != null ? clientName : "Client vô danh"));
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

            if ("SUCCESS".equals(result)) {
                sendResponse("REGISTER_SUCCESS", regUser.getName());
            } else {
                sendResponse("REGISTER_FAIL", result);
            }
        } catch (IllegalArgumentException e) {
            // --- ĐÃ THÊM LOG ĐỂ BẮT BỆNH NẾU DỮ LIỆU BỊ SAI ---
            System.err.println("Lỗi Mapping JSON sang User (Đăng ký): " + e.getMessage());
            sendResponse("ERROR", "Dữ liệu đăng ký không hợp lệ!");
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
                MultiThreadedServer.broadcast("[System]: " + this.clientName + " đã tham gia hệ thống đấu giá.", this);
            } else {
                sendResponse("LOGIN_FAIL", "Sai tài khoản hoặc mật khẩu");
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Lỗi Mapping JSON sang User (Đăng nhập): " + e.getMessage());
            sendResponse("ERROR", "Dữ liệu đăng nhập không hợp lệ!");
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
            System.err.println("Lỗi đóng gói JSON gửi đi: " + e.getMessage());
        }
    }

    public void sendMessage(String message) {
        sendResponse("CHAT", message);
    }

    private void closeConnection() {
        MultiThreadedServer.removeClient(this);
        if (clientName != null) {
            MultiThreadedServer.broadcast("[System]: " + clientName + " đã ngắt kết nối.", this);
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