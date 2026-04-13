package server;

import controller.UserController;
import model.User;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String clientName;

    // --- THÊM CÁC CONTROLLER VÀO ĐÂY ---
    private UserController userController;
    // Tương lai bạn sẽ thêm:
    // private BidderController bidderController;
    // private SellerController sellerController;

    // Lưu trữ thông tin người dùng NẾU họ đã đăng nhập thành công
    private User loggedInUser = null;

    public ClientHandler(Socket socket, UserController userController) {
        this.socket = socket;
        this.userController = userController; // Nhận Controller từ MultiThreadedServer truyền vào

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

            // XÓA BỎ đoạn bắt nhập ID thủ công ở đây, vì giờ chúng ta dùng giao diện Đăng nhập/Đăng ký rồi

            String clientMessage;
            // Vòng lặp chính: Lắng nghe mọi yêu cầu từ Client
            while ((clientMessage = in.readLine()) != null) {

                if ("STOP".equalsIgnoreCase(clientMessage.trim()) || "QUIT".equalsIgnoreCase(clientMessage.trim())) {
                    System.out.println((clientName != null ? clientName : "Client vô danh") + " đã chủ động ngắt kết nối.");
                    break; // Thoát vòng lặp, chạy xuống khối finally
                }

                // --- BẮT ĐẦU XỬ LÝ LỆNH (PROTOCOL) ---
                System.out.println("Nhận từ Client: " + clientMessage);

                // Tách chuỗi bằng dấu "|" (Cần dùng "\\|" vì | là ký tự đặc biệt trong Regex)
                String[] parts = clientMessage.split("\\|");

                if (parts.length == 0) continue;

                String command = parts[0];

                switch (command) {
                    case "REGISTER":
                        handleRegister(parts);
                        break;

                    case "LOGIN":
                        handleLogin(parts);
                        break;

                    // THÊM CÁC LỆNH KHÁC VÀO ĐÂY TRONG TƯƠNG LAI
                    // case "PLACE_BID":
                    // case "ADD_AUCTION":

                    default:
                        // Nếu không trúng lệnh nào, coi như tin nhắn chat bình thường (tạm thời giữ lại tính năng này nếu nhóm bạn cần test)
                        System.out.println("Nhận lệnh không xác định: " + command);
                        out.println("ERROR|Unrecognized command");
                        break;
                }
            }
        } catch (IOException e) {
            System.out.println("Mất kết nối với " + (clientName != null ? clientName : "Client vô danh"));
        } finally {
            closeConnection();
        }
    }

    // ==========================================
    // CÁC HÀM XỬ LÝ LỆNH CHI TIẾT
    // ==========================================

    private void handleRegister(String[] parts) {
        // Cú pháp chuẩn: REGISTER|Username|Password|Name|Role
        if (parts.length != 5) {
            out.println("ERROR|Invalid arguments for REGISTER");
            return;
        }

        String username = parts[1];
        String password = parts[2];
        String name = parts[3];
        String role = parts[4];

        // Gọi Controller xử lý
        String result = userController.register(username, password, name, role);

        // Trả kết quả về Client
        if ("SUCCESS".equals(result)) {
            out.println("REGISTER_SUCCESS|" + name);
        } else {
            out.println("REGISTER_FAIL|" + result); // result chứa thông báo lỗi (VD: Username đã tồn tại)
        }
    }

    private void handleLogin(String[] parts) {
        // Cú pháp chuẩn: LOGIN|Username|Password
        if (parts.length != 3) {
            out.println("ERROR|Invalid arguments for LOGIN");
            return;
        }

        String username = parts[1];
        String password = parts[2];

        // Gọi Controller xử lý
        User user = userController.login(username, password);

        if (user != null) {
            this.loggedInUser = user; // Lưu lại thông tin trên Server để biết ai đang thao tác
            this.clientName = user.getName();
            out.println("LOGIN_SUCCESS|" + user.getId() + "|" + user.getName() + "|" + user.getRole());
            System.out.println(this.clientName + " đã đăng nhập thành công.");
        } else {
            out.println("LOGIN_FAIL|Sai tài khoản hoặc mật khẩu");
        }
    }

    // ==========================================
    // CÁC HÀM CƠ BẢN CŨ GIỮ NGUYÊN (Kích người dùng, ngắt kết nối...)
    // ==========================================

    public void sendMessage(String message) {
        out.println(message);
    }

    private void closeConnection() {
        MultiThreadedServer.removeClient(this);
        if (clientName != null) {
            // Chỉ thông báo nếu họ đã từng đăng nhập thành công
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
        try {
            out.println("KICKED|" + reason); // Đổi format một chút cho nhất quán
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}