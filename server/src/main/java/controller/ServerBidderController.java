package controller;

import database.DatabaseManager;
import model.Auction;
import model.Bidder;
import model.BidTransaction;
import model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static utils.ConsoleColors.*;

public class ServerBidderController {

    public boolean placeBidOnAuction(User currentUser, Auction auction, double newMaxBid) {
        if (auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("[Security]: " + RED + "You cannot bid on your own auction" + RESET);
            return false;
        }

        Bidder bidder = new Bidder(currentUser);
        // Nhận về danh sách TẤT CẢ các giao dịch (của người và của bot tự vệ)
        List<BidTransaction> newTxns = auction.placeBid(bidder, newMaxBid);

        if (newTxns != null && !newTxns.isEmpty()) {
            String insertTransactionSql  = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, bid_time) VALUES (?, ?, ?, ?, ?)";
            String updateAuctionPriceSql = "UPDATE auctions SET current_price = ?, end_time = ? WHERE id = ?";

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // 1. Lưu HÀNG LOẠT (Batch) tất cả các transaction
                    try (PreparedStatement pstmt1 = conn.prepareStatement(insertTransactionSql)) {
                        for (BidTransaction txn : newTxns) {
                            pstmt1.setString(1, txn.getId());
                            pstmt1.setString(2, auction.getId());
                            pstmt1.setString(3, txn.getBidder().getId());
                            pstmt1.setDouble(4, txn.getBidAmount());
                            pstmt1.setString(5, LocalDateTime.now().toString());
                            pstmt1.addBatch(); // Xếp vào hàng đợi
                        }
                        pstmt1.executeBatch(); // Thực thi đồng loạt
                    }

                    // 2. Cập nhật giá và thời gian (chống bắn tỉa)
                    try (PreparedStatement pstmt2 = conn.prepareStatement(updateAuctionPriceSql)) {
                        pstmt2.setDouble(1, auction.getCurrentPrice());
                        pstmt2.setString(2, auction.getEndTime().toString());
                        pstmt2.setString(3, auction.getId());
                        pstmt2.executeUpdate();
                    }

                    conn.commit();
                    System.out.println("[System]: Bid sequence recorded successfully for auction \"" + YELLOW + auction.getId() + RESET + "\"");
                    return true;

                } catch (SQLException e) {
                    conn.rollback();
                    System.out.println("[Error]: Database Transaction Error: " + RED + e.getMessage() + RESET);
                }
            } catch (SQLException e) {
                System.out.println("[Error]: Database Connection Error: " + RED + e.getMessage() + RESET);
            }
        }
        return false;
    }

    public boolean setupAutoBid(User currentUser, Auction auction, double maxBid, double increment) {
        if (auction.getSeller().getId().equals(currentUser.getId())) {
            System.out.println("[Security]: " + RED + "You cannot set auto-bid on your own auction" + RESET);
            return false;
        }

        Bidder bidder = new Bidder(currentUser);
        List<BidTransaction> newTxns = auction.registerAutoBid(bidder, maxBid, increment);

        if (newTxns != null) {
            String sqlAuto = "INSERT OR REPLACE INTO auto_bids (id, auction_id, bidder_id, max_bid, increment_amount, is_active) VALUES (?, ?, ?, ?, ?, 1)";
            String insertTransactionSql  = "INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, bid_time) VALUES (?, ?, ?, ?, ?)";
            String updateAuctionPriceSql = "UPDATE auctions SET current_price = ? WHERE id = ?";

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // 1. Lưu cấu hình Bot
                    try (PreparedStatement pstmt = conn.prepareStatement(sqlAuto)) {
                        pstmt.setString(1, "AB-" + System.currentTimeMillis());
                        pstmt.setString(2, auction.getId());
                        pstmt.setString(3, bidder.getId());
                        pstmt.setDouble(4, maxBid);
                        pstmt.setDouble(5, increment);
                        pstmt.executeUpdate();
                    }

                    // 2. Nếu Bot vừa bật mà nhảy vào đấu giá luôn, lưu lịch sử của nó
                    if (!newTxns.isEmpty()) {
                        try (PreparedStatement pstmt1 = conn.prepareStatement(insertTransactionSql)) {
                            for (BidTransaction txn : newTxns) {
                                pstmt1.setString(1, txn.getId());
                                pstmt1.setString(2, auction.getId());
                                pstmt1.setString(3, txn.getBidder().getId());
                                pstmt1.setDouble(4, txn.getBidAmount());
                                pstmt1.setString(5, LocalDateTime.now().toString());
                                pstmt1.addBatch();
                            }
                            pstmt1.executeBatch();
                        }
                        try (PreparedStatement pstmt2 = conn.prepareStatement(updateAuctionPriceSql)) {
                            pstmt2.setDouble(1, auction.getCurrentPrice());
                            pstmt2.setString(2, auction.getId());
                            pstmt2.executeUpdate();
                        }
                    }

                    conn.commit();
                    System.out.println("[System]: Auto-Bid configuration saved for \"" + YELLOW + currentUser.getName() + RESET + "\"");
                    return true;

                } catch (SQLException e) {
                    conn.rollback();
                    System.out.println("[Error]: Database Error saving AutoBid: " + RED + e.getMessage() + RESET);
                }
            } catch (SQLException e) {
                System.out.println("[Error]: Database Connection Error: " + RED + e.getMessage() + RESET);
            }
        }
        return false;
    }
}