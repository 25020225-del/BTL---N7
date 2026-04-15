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
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    // 1. BẢN VÁ JACKSON: Dạy Jackson lờ đi các biến thừa (như info, command)
    private ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // Hàm callback để gửi kết quả về giao diện
    private Consumer<NetworkMessage> onMessageReceived;

    public NetworkClient(String serverAddress, int port) {
        try {
            socket = new Socket(serverAddress, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Bật luồng chạy ngầm để liên tục nghe ngóng Server
            Thread listenerThread = new Thread(this::listenToServer);
            listenerThread.setDaemon(true); // Tự động tắt khi tắt App
            listenerThread.start();
            System.out.println("Đã kết nối tới Server thành công!");

        } catch (IOException e) {
            System.err.println("Không thể kết nối tới Server: " + e.getMessage());
        }
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
            System.err.println("Lỗi: Không thể gửi lệnh '" + command + "' vì chưa kết nối tới Server!");
            return;
        }

        try {
            NetworkMessage msg = new NetworkMessage(command, data);
            String json = mapper.writeValueAsString(msg);

            // ĐÃ THÊM: In ra console để theo dõi luồng mạng
            System.out.println("[Network] Đang gửi đi: " + json);

            out.println(json);
            out.flush(); // LỆNH BẮT BUỘC: Ép đẩy dữ liệu qua mạng ngay lập tức

        } catch (Exception e) {
            System.err.println("Lỗi đóng gói JSON: " + e.getMessage());
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
                            System.out.println("[System] Đang mở trình duyệt tới: " + url);
                        }
                    } catch (Exception e) {
                        System.out.println("[System] Không thể mở trình duyệt: " + e.getMessage());
                    }
                    continue; // Xử lý xong lệnh này thì bỏ qua các dòng dưới, quay lại vòng lặp
                }

                // 4. TÍNH NĂNG KICKED (BỊ ADMIN ĐUỔI)
                if ("KICKED".equals(command)) {
                    System.out.println("Bạn đã bị Admin đuổi khỏi Server. Lý do: " + response.getData());
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
            System.out.println("Mất kết nối với Server.");
        }
    }
}