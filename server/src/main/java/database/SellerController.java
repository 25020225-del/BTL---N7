package database;

import model.Auction;
import model.Item;
import model.Seller;
import java.time.LocalDateTime;

public class SellerController {

    // ==========================================
    // 1. THÊM SẢN PHẨM (Create)
    // ==========================================
    public Auction addAuction(Seller seller, Item item, double bidIncrement, LocalDateTime startTime, LocalDateTime endTime) {
        // Gọi Constructor của Auction mà chúng ta đã hoàn thiện.
        // Nó sẽ tự động lo việc set trạng thái là PENDING hay APPROVED dựa vào độ uy tín của Seller!
        Auction newAuction = new Auction("AUC-" + System.currentTimeMillis(), item, seller, bidIncrement, startTime, endTime);

        System.out.println("Success: Seller" + seller.getName() + " has created a new auction request for the product.: " + item.getItemName());
        return newAuction;
    }

    // ==========================================
    // 2. SỬA SẢN PHẨM (Update)
    // ==========================================
    public boolean editAuction(Seller seller, Auction auction, String newName, String newDesc, double newStartPrice, LocalDateTime newStartTime, LocalDateTime newEndTime) {

        // BẢO MẬT 1: Kiểm tra xem người đang đòi sửa có đúng là chủ của món hàng này không?
        if (!auction.getSeller().getId().equals(seller.getId())) {
            System.out.println("Security error: You are not the owner of this auction!");
            return false;
        }

        // BẢO MẬT 2: Chỉ cho phép sửa khi phiên đấu giá CHƯA BẮT ĐẦU (Đang chờ duyệt hoặc Đã duyệt).
        // Nếu đang RUNNING (đã có người đặt giá) mà cho sửa giá khởi điểm thì hệ thống sẽ loạn!
        if (auction.getStatus().equals(Auction.STATUS_RUNNING) ||
                auction.getStatus().equals(Auction.STATUS_CLOSED) ||
                auction.getStatus().equals(Auction.STATUS_DELETED)) {
            System.out.println("Error: Cannot edit information while the auction is ongoing, has ended, or has been deleted!");
            return false;
        }

        // Thực hiện cập nhật thông tin
        auction.getItem().setItemName(newName);
        auction.getItem().setDescription(newDesc);

        // Cập nhật giá khởi điểm và đồng bộ luôn với giá hiện tại (vì chưa có ai đấu giá)
        auction.getItem().setStartingPrice(newStartPrice);
        auction.setCurrentPrice(newStartPrice);

        // Cập nhật thời gian
        auction.setStartTime(newStartTime);
        auction.setEndTime(newEndTime);

        // Mẹo: Nếu đơn từng bị Admin từ chối (REJECTED), sau khi sửa xong sẽ tự động quay về trạng thái Chờ duyệt (PENDING)
        if (auction.getStatus().equals(Auction.STATUS_REJECTED)) {
            auction.setStatus(Auction.STATUS_PENDING);
            System.out.println("The order has been corrected and resubmitted to the admin for approval.");
        }

        System.out.println("Success: The auction has been updated." + auction.getId());
        return true;
    }

    // ==========================================
    // 3. XÓA SẢN PHẨM (Delete)
    // ==========================================
    public boolean deleteAuction(Seller seller, Auction auction) {

        // BẢO MẬT 1: Kiểm tra chính chủ
        if (!auction.getSeller().getId().equals(seller.getId())) {
            System.out.println("Security error: You do not have permission to delete this product!");
            return false;
        }

        // Dùng cơ chế Soft Delete (Đổi trạng thái thành DELETED) như đã thống nhất lúc trước
        auction.setStatus(Auction.STATUS_DELETED);
        System.out.println("Success: Seller" + seller.getName() + " has canceled/deleted the auction. " + auction.getId());
        return true;
    }
}