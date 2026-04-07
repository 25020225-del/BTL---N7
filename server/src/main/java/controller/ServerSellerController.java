package controller;

import database.DatabaseManager;
import model.Auction;
import model.Item;
import model.Seller;
import model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class ServerSellerController {

    // ==========================================
    // 1. THÊM SẢN PHẨM (Lưu vào SQLite)
    // ==========================================
    public Auction addAuction(User currentUser, Item item, double bidIncrement, LocalDateTime startTime, LocalDateTime endTime) {

        Seller seller = new Seller(currentUser);
        String auctionId = "AUC-" + System.currentTimeMillis();

        // Tạo đối tượng Auction để trả về cho Client hiển thị (Tự set status PENDING/APPROVED)
        Auction newAuction = new Auction(auctionId, item, seller, bidIncrement, startTime, endTime);

        // Lưu vào Database
        String sql = "INSERT INTO auctions (id, item_name, description, starting_price, current_price, bid_increment, start_time, end_time, status, seller_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newAuction.getId());
            pstmt.setString(2, item.getItemName());
            pstmt.setString(3, item.getDescription());
            pstmt.setDouble(4, item.getStartingPrice());
            pstmt.setDouble(5, newAuction.getCurrentPrice()); // Ban đầu bằng starting_price
            pstmt.setDouble(6, bidIncrement);
            pstmt.setString(7, startTime.toString()); // Lưu thời gian thành chuỗi (String)
            pstmt.setString(8, endTime.toString());
            pstmt.setString(9, newAuction.getStatus());
            pstmt.setString(10, seller.getId());

            pstmt.executeUpdate();
            System.out.println("System (DB): Seller " + seller.getName() + " created auction: " + item.getItemName());

        } catch (SQLException e) {
            System.err.println("Database error during addAuction: " + e.getMessage());
            return null; // Báo lỗi nếu không lưu được
        }

        return newAuction;
    }

    // ==========================================
    // 2. SỬA SẢN PHẨM (Cập nhật SQLite)
    // ==========================================
    public boolean editAuction(User currentUser, Auction auction, String newName, String newDesc, double newStartPrice, LocalDateTime newStartTime, LocalDateTime newEndTime) {

        // Kiểm tra quyền
        if (!auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("Security error: You are not the owner of this auction!");
            return false;
        }

        // Chỉ cho sửa khi chưa chạy
        if (auction.getStatus().equals(Auction.STATUS_RUNNING) ||
                auction.getStatus().equals(Auction.STATUS_FINISHED) ||
                auction.getStatus().equals(Auction.STATUS_DELETED)) {
            System.out.println("Error: Cannot edit information while the auction is ongoing, has ended, or has been deleted!");
            return false;
        }

        // Trạng thái mới (nếu bị CANCELED thì tự về PENDING)
        String newStatus = auction.getStatus().equals(Auction.STATUS_CANCELED) ? Auction.STATUS_PENDING : auction.getStatus();

        String sql = "UPDATE auctions SET item_name = ?, description = ?, starting_price = ?, current_price = ?, start_time = ?, end_time = ?, status = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newName);
            pstmt.setString(2, newDesc);
            pstmt.setDouble(3, newStartPrice);
            pstmt.setDouble(4, newStartPrice); // Đồng bộ current_price
            pstmt.setString(5, newStartTime.toString());
            pstmt.setString(6, newEndTime.toString());
            pstmt.setString(7, newStatus);
            pstmt.setString(8, auction.getId());

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                // Đồng bộ cập nhật luôn đối tượng trên RAM để code cũ không bị lỗi
                auction.getItem().setItemName(newName);
                auction.getItem().setDescription(newDesc);
                auction.getItem().setStartingPrice(newStartPrice);
                auction.setCurrentPrice(newStartPrice);
                auction.setStartTime(newStartTime);
                auction.setEndTime(newEndTime);
                auction.setStatus(newStatus);

                System.out.println("System (DB): Auction " + auction.getId() + " updated successfully.");
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Database error during editAuction: " + e.getMessage());
        }
        return false;
    }

    // ==========================================
    // 3. XÓA SẢN PHẨM (Soft Delete trên SQLite)
    // ==========================================
    public boolean deleteAuction(User currentUser, Auction auction) {

        if (!auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("Security error: You do not have permission to delete this product!");
            return false;
        }

        // Soft Delete: Chỉ Update trạng thái thành DELETED chứ không dùng lệnh DELETE FROM
        String sql = "UPDATE auctions SET status = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, Auction.STATUS_DELETED);
            pstmt.setString(2, auction.getId());

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                auction.setStatus(Auction.STATUS_DELETED); // Đồng bộ đối tượng trên RAM
                System.out.println("System (DB): Auction " + auction.getId() + " has been DELETED by " + currentUser.getName());
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Database error during deleteAuction: " + e.getMessage());
        }
        return false;
    }
}