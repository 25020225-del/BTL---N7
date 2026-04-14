package controller;

import database.DatabaseManager;
import model.Auction;
import model.Bidder;
import model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class ServerBidderController {

    // ==========================================
    // 1. ĐẶT GIÁ THỦ CÔNG (Lưu lịch sử vào SQLite)
    // ==========================================
    public boolean placeBidOnAuction(User currentUser, Auction auction, double newMaxBid) {

        // 1. Bảo mật: Chống tự đấu giá đồ của mình
        if (auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("Security error: You cannot bid on your own auction!");
            return false;
        }

        // 2. Hóa thân User thành Bidder
        Bidder bidder = new Bidder(currentUser);

        // 3. Thực hiện logic đặt giá trên RAM
        boolean isSuccess = auction.placeBid(bidder, newMaxBid);

        if (isSuccess) {
            // 4. NẾU THÀNH CÔNG -> GHI VÀO DATABASE BẰNG TRANSACTION
            String insertTransactionSql = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, bid_time) VALUES (?, ?, ?, ?, ?)";
            String updateAuctionPriceSql = "UPDATE auctions SET current_price = ? WHERE id = ?";

            try (Connection conn = DatabaseManager.getConnection()) {
                // Tắt Auto Commit để đảm bảo cả 2 lệnh cùng thành công hoặc cùng thất bại
                conn.setAutoCommit(false);

                try {
                    // Ghi lịch sử đặt giá
                    try (PreparedStatement pstmt1 = conn.prepareStatement(insertTransactionSql)) {
                        pstmt1.setString(1, "TXN-" + System.currentTimeMillis());
                        pstmt1.setString(2, auction.getId());
                        pstmt1.setString(3, bidder.getId());
                        pstmt1.setDouble(4, auction.getCurrentPrice()); // Giá hiện tại sau khi đặt thành công
                        pstmt1.setString(5, LocalDateTime.now().toString());
                        pstmt1.executeUpdate();
                    }

                    // Cập nhật giá hiện tại của phiên đấu giá trong bảng auctions
                    try (PreparedStatement pstmt2 = conn.prepareStatement(updateAuctionPriceSql)) {
                        pstmt2.setDouble(1, auction.getCurrentPrice());
                        pstmt2.setString(2, auction.getId());
                        pstmt2.executeUpdate();
                    }

                    conn.commit(); // Chốt giao dịch thành công
                    System.out.println("System (DB): Bid recorded for " + currentUser.getName() + " on " + auction.getId());
                    return true;

                } catch (SQLException e) {
                    conn.rollback(); // Có lỗi thì hủy bỏ toàn bộ để tránh sai lệch dữ liệu
                    System.err.println("Database Transaction Error: " + e.getMessage());
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    // ==========================================
    // 2. ĐĂNG KÝ AUTO-BID (Lưu vào SQLite)
    // ==========================================
    public boolean setupAutoBid(User currentUser, Auction auction, double maxBid, double increment) {

        // 1. Bảo mật: Không cho tự Auto-Bid đồ của chính mình
        if (auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("Security error: You cannot set auto-bid on your own auction!");
            return false;
        }

        // 2. Hóa thân User thành Bidder
        Bidder bidder = new Bidder(currentUser);

        // 3. Thực hiện đăng ký trên RAM
        boolean isSuccess = auction.registerAutoBid(bidder, maxBid, increment);

        if (isSuccess) {
            // 4. LƯU CẤU HÌNH VÀO DATABASE
            // Dùng INSERT OR REPLACE để tự động cập nhật nếu đã tồn tại cấu hình cũ
            String sql = "INSERT OR REPLACE INTO auto_bids (id, auction_id, bidder_id, max_bid, increment_amount, is_active) " +
                    "VALUES (?, ?, ?, ?, ?, 1)";

            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, "AB-" + System.currentTimeMillis());
                pstmt.setString(2, auction.getId());
                pstmt.setString(3, bidder.getId());
                pstmt.setDouble(4, maxBid);
                pstmt.setDouble(5, increment);

                pstmt.executeUpdate();
                System.out.println("System (DB): Auto-Bid configuration saved for " + currentUser.getName());
                return true;

            } catch (SQLException e) {
                System.err.println("Database Error saving AutoBid: " + e.getMessage());
            }
        }
        return false;
    }
}