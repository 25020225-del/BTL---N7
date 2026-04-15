package client; // Giữ nguyên package của bạn

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import network.NetworkMessage;
import javafx.application.Platform;

import java.awt.Desktop;
import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.util.function.Consumer;

public class NetworkClient {
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    // 1. BẢN VÁ JACKSON: Dạy Jackson lờ đi các biến thừa (như info, command)
    private ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // Hàm callback để gửi kết quả về giao diện
    private Consumer<NetworkMessage> onMessageReceived;

    public NetworkClient(String serverAddress, int port) {
        int maxRetries = 5;
        int tries = 0;
        int retryDelayMs = 2000;
        System.out.println("=====================================");
        System.out.println("[System]: Trying to connect to server");
        System.out.println("Attempting to connect"+ANSI_YELLOW);
        for (int i = 0; i < maxRetries; i++) {
            try {
                socket = new Socket(serverAddress, port);

                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                Thread listenerThread = new Thread(this::listenToServer);
                listenerThread.setDaemon(true);
                listenerThread.start();

                System.out.println(ANSI_RESET+"[System]: Successfully connected");
                return;

            } catch (IOException e) {
                System.out.println("Failed at "+(i+1)+". try: " + e.getMessage());
                tries++;
                if (i<maxRetries-1){
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        System.out.println(ANSI_BLUE+"[System]: Failed after 5 tries. Opening offline application"+ANSI_RESET);
    }

    // 2. ÁO GIÁP: Kiểm tra xem ống nước đã được nối chưa
    public boolean isConnected() {
        return socket != null && socket.isConnected() && out != null;
    }

    // Giao diện (Controller) sẽ dùng hàm này để đăng ký nhận thông báo
    public void setOnMessageReceived(Consumer<NetworkMessage> callback) {
        this.onMessageReceived = callback;
    }

    // Gửi dữ liệu đi dưới dạng JSON
    public void sendMessage(String command, Object data) {
        // Chặn lỗi null ngay từ đầu nếu rớt mạng
        if (!isConnected()) {
            System.out.println("[Error]: Cannot send command: '" + command + "' due to not connected");
            return;
        }

        try {
            NetworkMessage msg = new NetworkMessage(command, data);
            String json = mapper.writeValueAsString(msg);
            out.println(json);
        } catch (Exception e) {
            System.out.println("[Error]: JSON package error: " + e.getMessage());
        }
    }

    // Luồng nghe ngóng Server
    private void listenToServer() {
        try {
            String jsonMessage;
            while ((jsonMessage = in.readLine()) != null) {
                NetworkMessage response = mapper.readValue(jsonMessage, NetworkMessage.class);
                String command = response.getCommand();

                // 3. TÍNH NĂNG REDIRECT (MỞ TRÌNH DUYỆT WEB)
                if ("REDIRECT".equals(command)) {
                    String url = (String) response.getData();
                    try {
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                            Desktop.getDesktop().browse(new URI(url));
                            System.out.println("[System]: Redirecting to: " + url);
                        }
                    } catch (Exception e) {
                        System.out.println("[Error]: Cannot redirect: " + e.getMessage());
                    }
                    continue; // Xử lý xong lệnh này thì bỏ qua các dòng dưới, quay lại vòng lặp
                }

                // 4. TÍNH NĂNG KICKED (BỊ ADMIN ĐUỔI)
                if ("KICKED".equals(command)) {
                    System.out.println("[System]: You have been kicked. Reason: " + response.getData());
                    // Vẫn đẩy về giao diện để hiện Pop-up cảnh báo (nếu có làm)
                    if (onMessageReceived != null) {
                        Platform.runLater(() -> onMessageReceived.accept(response));
                    }
                    // Đợi 1 giây cho Pop-up kịp hiện rồi tắt app
                    try { Thread.sleep(1000); } catch (Exception ignored) {}
                    System.exit(0);
                }

                // NẾU LÀ CÁC KẾT QUẢ BÌNH THƯỜNG KHÁC (LOGIN_SUCCESS, REGISTER_SUCCESS...)
                // -> Đẩy về luồng Giao diện (JavaFX Thread) để xử lý Form
                if (onMessageReceived != null) {
                    Platform.runLater(() -> onMessageReceived.accept(response));
                }
            }
        } catch (IOException e) {
            System.out.println("[Error]: Lost connection to server: " + e.getMessage());
        }
    }
}