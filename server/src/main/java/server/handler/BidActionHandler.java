package server.handler;

import controller.ServerBidderController;
import database.dao.AuctionDAO;
import model.auction.Auction;
import model.user.User;
import network.NetworkMessage;
import server.ClientHandler;

import java.util.Map;

public class BidActionHandler implements CommandHandler {
    private final ServerBidderController bidderCtrl;
    private final AuctionDAO auctionDAO;

    // Sử dụng Dependency Injection để truyền Controller và DAO vào thông qua Constructor
    public BidActionHandler(ServerBidderController bidderCtrl, AuctionDAO auctionDAO) {
        this.bidderCtrl = bidderCtrl;
        this.auctionDAO = auctionDAO;
    }

    @Override
    public void handle(NetworkMessage message, ClientHandler client) {
        if ("PLACE_BID".equals(message.getCommand())) {
            handlePlaceBid(message.getData(), client);
        }
    }

    @SuppressWarnings("unchecked") // Tắt cảnh báo ép kiểu (vạch vàng) của IDE
    private void handlePlaceBid(Object data, ClientHandler client) {
        try {
            User currentUser = client.getUser();
            if (currentUser == null) {
                client.sendResponse("ERROR", "You must be logged in to place a bid.");
                return;
            }

            Map<String, Object> bidData = (Map<String, Object>) data;
            String auctionId = (String) bidData.get("auctionId");
            double amount = Double.parseDouble(bidData.get("bidAmount").toString());
            
            // Lấy isBot, nếu client không gửi lên thì mặc định là false (người dùng thật đặt giá)
            boolean isBot = false;
            if (bidData.containsKey("isAutoBid")) {
                isBot = (boolean) bidData.get("isAutoBid");
            }

            // CÁCH 2: Gọi xuống Database thông qua AuctionDAO để lấy thông tin phiên đấu giá mới nhất
            Auction auction = auctionDAO.getAuctionById(auctionId);

            if (auction == null) {
                client.sendResponse("ERROR", "Auction not found in database.");
                return;
            }

            // Đẩy sang ServerBidderController xử lý logic trừ tiền và đặt giá
            bidderCtrl.placeBidOnAuction(currentUser, auction, amount, isBot).thenAccept(success -> {
                if (success) {
                    client.sendResponse("BID_SUCCESS", "Your bid has been placed successfully!");
                } else {
                    client.sendResponse("ERROR", "Bid failed. Please check your balance or bid amount.");
                }
            });

        } catch (Exception e) {
            client.sendResponse("ERROR", "Invalid bid data format.");
            e.printStackTrace();
        }
    }
}