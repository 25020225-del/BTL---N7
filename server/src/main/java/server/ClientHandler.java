package server;

import com.fasterxml.jackson.databind.ObjectMapper;
import controller.UserController;
import model.User;
import network.NetworkMessage; // Nhớ đảm bảo đúng tên package chứa NetworkMessage bên common

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String clientName;

    private UserController userController;
    private User loggedInUser = null;

    // Khởi tạo công cụ chuyển đổi JSON của Jackson
    private final ObjectMapper mapper = new ObjectMapper();

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
            System.out.println("Unknown has just connected.");

            String jsonMessage;
            // Vòng lặp chính: Lắng nghe mọi JSON từ Client gửi lên
            while ((jsonMessage = in.readLine()) != null) {

                // Thoát vòng lặp nếu Client muốn ngắt kết nối
                if ("STOP".equalsIgnoreCase(jsonMessage.trim()) || "QUIT".equalsIgnoreCase(jsonMessage.trim())) {
                    System.out.println((clientName != null ? clientName : "Unknown") + " has disconnected.");
                    break;
                }


                try {
                    // Dịch chuỗi JSON thành đối tượng NetworkMessage
                    NetworkMessage message = mapper.readValue(jsonMessage, NetworkMessage.class);
                    String command = message.getCommand();

                    // Điều hướng xử lý dựa trên Tên lệnh
                    switch (command) {
                        case "REGISTER":
                            handleRegister(message.getData());
                            break;

                        case "LOGIN":
                            handleLogin(message.getData());
                            break;

                        default:
                            System.out.println("Unrecognized command: " + command);
                            sendResponse("ERROR", "Unrecognized command");
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("JSON parsing error: " + e.getMessage());
                    sendResponse("ERROR", "Invalid JSON format");
                }
            }
        } catch (IOException e) {
            System.out.println("Lost connection with" + (clientName != null ? clientName : "Unknown"));
        } finally {
            closeConnection();
        }
    }

    // ==========================================
    // CÁC HÀM XỬ LÝ LỆNH CHI TIẾT DÙNG JACKSON
    // ==========================================

    private void handleRegister(Object data) {
        try {
            // Ép phần 'data' (đang là Object chung) về đúng khuôn mẫu User
            User regUser = mapper.convertValue(data, User.class);

            // Gọi Controller xử lý
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
            sendResponse("ERROR", "Missing user data for registration");
        }
    }

    private void handleLogin(Object data) {
        try {
            // Ép phần 'data' về khuôn mẫu User (Client chỉ cần điền userName và userPass)
            User loginAttempt = mapper.convertValue(data, User.class);

            // Gọi Controller kiểm tra Database
            User user = userController.login(loginAttempt.getUserName(), loginAttempt.getUserPass());

            if (user != null) {
                this.loggedInUser = user;
                this.clientName = user.getName();

                // Trả nguyên đối tượng User (đã có đủ thông tin) về cho Client
                sendResponse("LOGIN_SUCCESS", user);
                System.out.println(this.clientName + " has logged in successfully.");
            } else {
                sendResponse("LOGIN_FAIL", "Incorrect account or password");
            }
        } catch (IllegalArgumentException e) {
            sendResponse("ERROR", "Missing user data for login");
        }
    }

    // ==========================================
    // HÀM GỬI DỮ LIỆU ĐÓNG GÓI JSON VỀ CLIENT
    // ==========================================
    public void sendResponse(String command, Object data) {
        try {
            // Đóng gói kết quả vào NetworkMessage
            NetworkMessage responseMsg = new NetworkMessage(command, data);

            // Dịch thành chuỗi JSON và gửi đi
            String jsonOutput = mapper.writeValueAsString(responseMsg);
            out.println(jsonOutput);

        } catch (Exception e) {
            System.err.println("Error when packaging JSON to send: " + e.getMessage());
        }
    }

    // ==========================================
    // CÁC HÀM CƠ BẢN (Đã cập nhật dùng hàm sendResponse)
    // ==========================================

    public void sendMessage(String message) {
        sendResponse("CHAT", message);
    }

    private void closeConnection() {
        MultiThreadedServer.removeClient(this);
        if (clientName != null) {
            MultiThreadedServer.broadcast("[System]: " + clientName + " has disconnected.", this);
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
}