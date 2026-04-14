package controller;

// Nằm ở phía Server: package controller;
import model.Admin;
import model.Seller;
import model.Auction;

public class ServerAdminController {

    // Hàm này là hành động "Duyệt sản phẩm"
    public boolean approveAuction(Admin nguoiThucHien, Auction phienDauGia) {

        // 1. Kiểm tra xem người yêu cầu có đúng là Admin không?
        if (nguoiThucHien == null || !nguoiThucHien.getRole().equals("Admin")) {
            System.out.println("Error: User does not have approval rights!");
            return false;
        }
        phienDauGia.setStatus(Auction.STATUS_OPEN);
        System.out.println("Success: Admin " + nguoiThucHien.getName() + " has approved the session " + phienDauGia.getId());

        // 4. (Sau này sẽ gọi thêm DAO ở đây để lưu lệnh UPDATE xuống Database)

        return true;
    }
    // 1. Quyền trao minh chứng "Good Seller"
    public void verifySeller(Admin admin, Seller seller) {
        if (admin.getRole().equals("Admin")) {
            seller.setGood(true);
            System.out.println("Admin " + admin.getName() + " đã xác nhận Seller " + seller.getName() + " là người bán uy tín!");
        }
    }

    // 2. Quyền bác bỏ đơn duyệt (khi đơn đang ở trạng thái PENDING)
    public void rejectAuctionRequest(Admin admin, Auction auction) {
        if (admin.getRole().equals("Admin")) {
            auction.setStatus(Auction.STATUS_CANCELED);
            System.out.println("The admin has rejected the auction request for the id:" + auction.getId());
        }
    }

    // 3. Quyền xóa sản phẩm (ngay cả khi đã được duyệt hoặc đang chạy)
    public void forceDeleteAuction(Admin admin, Auction auction) {
        if (admin.getRole().equals("Admin")) {
            auction.setStatus(Auction.STATUS_DELETED);
            // Sau khi xóa, có thể thêm logic gửi thông báo cho Seller/Bidder
            System.out.println("The admin has permanently deleted the auction: " + auction.getId());
        }
    }
}