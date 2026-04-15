package controller;

import model.Auction;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AuctionMonitor {

    // Tạo ra 1 luồng (thread) chạy ngầm chuyên trách
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Nơi chứa danh sách tất cả các phiên đấu giá đang có trên Server
    private List<Auction> allAuctions;

    public AuctionMonitor(List<Auction> allAuctions) {
        this.allAuctions = allAuctions;
    }

    // Hàm kích hoạt hệ thống giám sát
    public void startMonitoring() {
        System.out.println("[Monitor]: The automatic auction monitoring system has been launched.");

        // Cấu hình: Bắt đầu ngay lập tức (delay = 0), lặp lại sau mỗi 10 giây
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // Đi tuần tra toàn bộ danh sách phiên đấu giá
                for (Auction auction : allAuctions) {

                    // Để tối ưu hiệu năng: Chỉ kiểm tra những phiên đang ở trạng thái RUNNING
                    if (auction.getStatus().equals(Auction.STATUS_RUNNING)) {
                        // Gọi hàm chốt sổ bạn vừa viết ở Bước trước
                        auction.closeAuctionIfTimeIsUp();
                    }
                }
            } catch (Exception e) {
                // Bắt lỗi (Exception) để đảm bảo luồng ngầm không bị sập nếu có 1 phiên đấu giá bị lỗi dữ liệu
                System.out.println("Error during the bidding scan process: " + e.getMessage());
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    // Hàm tắt hệ thống (dùng khi tắt Server)
    public void stopMonitoring() {
        scheduler.shutdown();
        System.out.println("[Monitor]: The auction monitoring system has been turned off.");
    }
}