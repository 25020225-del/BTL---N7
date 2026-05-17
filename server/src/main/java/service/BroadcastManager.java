package service;

import server.ClientHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Động cơ Broadcast tốc độ cao sử dụng cơ chế Batching, Debouncing và Pub/Sub.
 * Tối ưu hóa việc gửi tin nhắn WebSocket/Socket cho hàng vạn Client.
 */
public class BroadcastManager {

    // 1. PUBLISH-SUBSCRIBE (PUB/SUB)
    // Lưu danh sách Client đang theo dõi (subscribe) từng phiên đấu giá cụ thể.
    // Dùng ConcurrentHashMap và ConcurrentHashMap.newKeySet() để đảm bảo Thread-Safe và Non-blocking.
    private static final Map<String, Set<ClientHandler>> topics = new ConcurrentHashMap<>();

    // 2. DEBOUNCING STATE
    // Map chứa Payload (JSON đã được serialize) MỚI NHẤT của phiên đấu giá.
    // Nếu có 1000 lượt bid trong 200ms, map này CHỈ LƯU lượt bid cuối cùng.
    private static final Map<String, String> pendingPayloads = new ConcurrentHashMap<>();

    // 3. SCHEDULER BATCHING
    // Chỉ cần 1 Thread duy nhất cực nhẹ để đóng gói dữ liệu định kỳ (mỗi 200ms).
    private static final ScheduledExecutorService batchScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Broadcast-Batch-Ticker");
        t.setDaemon(true);
        return t;
    });

    // 4. NIO/IO THREAD POOL
    // Pool nhỏ gọn dùng để thực hiện ghi dữ liệu ra Socket/Network.
    // Công thức chuẩn: Số Core CPU * 2
    private static final ExecutorService networkIoPool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2
    );

    // Kích hoạt nhịp tim của hệ thống (Tick) mỗi 200ms
    static {
        batchScheduler.scheduleAtFixedRate(BroadcastManager::flushPayloads, 0, 200, TimeUnit.MILLISECONDS);
    }

    /**
     * Client gọi hàm này khi mở xem một phiên đấu giá.
     */
    public static void subscribe(String auctionId, ClientHandler client) {
        topics.computeIfAbsent(auctionId, k -> ConcurrentHashMap.newKeySet()).add(client);
    }

    /**
     * Client gọi hàm này khi thoát phòng đấu giá hoặc ngắt kết nối.
     */
    public static void unsubscribe(String auctionId, ClientHandler client) {
        Set<ClientHandler> subscribers = topics.get(auctionId);
        if (subscribers != null) {
            subscribers.remove(client);
            // Dọn dẹp rác nếu phòng không còn ai
            if (subscribers.isEmpty()) {
                topics.remove(auctionId);
            }
        }
    }

    /**
     * Controller/Service gọi hàm này khi có giá mới.
     * TUYỆT ĐỐI KHÔNG gửi đi ngay, chỉ "ghi đè" (Debounce) trạng thái vào RAM.
     * Lưu ý: Hãy serialize object thành String JSON ở TẦNG NÀY (chỉ serialize 1 lần duy nhất).
     */
    public static void queueUpdate(String auctionId, String jsonPayload) {
        pendingPayloads.put(auctionId, jsonPayload);
    }

    /**
     * Hàm quét định kỳ. Đóng gói và xả dữ liệu ra Network.
     */
    private static void flushPayloads() {
        if (pendingPayloads.isEmpty()) return;

        // Bốc toàn bộ dữ liệu đang chờ và reset Map về rỗng cực nhanh
        Map<String, String> snapshot;
        synchronized (pendingPayloads) {
            snapshot = new ConcurrentHashMap<>(pendingPayloads);
            pendingPayloads.clear();
        }

        // Bắn dữ liệu tới các Subscriber
        snapshot.forEach((auctionId, payload) -> {
            Set<ClientHandler> subscribers = topics.get(auctionId);

            if (subscribers != null && !subscribers.isEmpty()) {
                // Đẩy tác vụ gửi vào I/O Pool để không làm nghẽn luồng Ticker
                networkIoPool.submit(() -> {
                    for (ClientHandler client : subscribers) {
                        try {
                            // client.sendMessage() bên trong nên dùng NIO non-blocking (vd: AsynchronousSocketChannel)
                            client.sendMessage(payload);
                        } catch (Exception e) {
                            // Bỏ qua client bị lỗi, có thể do đứt mạng đột ngột
                            unsubscribe(auctionId, client);
                        }
                    }
                });
            }
        });
    }

    /**
     * Dọn dẹp tài nguyên khi Server tắt.
     */
    public static void shutdown() {
        batchScheduler.shutdownNow();
        networkIoPool.shutdownNow();
    }
}