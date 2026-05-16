package server.handler;

import controller.ServerBidderController;
import database.dao.AuctionDAO;
import exception.AuctionExceptions;
import network.ErrorPayload;
import model.auction.Auction;
import model.user.User;
import network.NetworkMessage;
import server.ClientHandler;

import java.util.Map;

public class BidActionHandler implements CommandHandler {
    private final ServerBidderController bidderCtrl;
    private final AuctionDAO auctionDAO;

    public BidActionHandler(ServerBidderController bidderCtrl, AuctionDAO auctionDAO) {
        this.bidderCtrl = bidderCtrl;
        this.auctionDAO = auctionDAO;
    }

    @Override
    public void handle(NetworkMessage message, ClientHandler client) throws Exception {
        if ("PLACE_BID".equals(message.getCommand())) {
            handlePlaceBid(message.getData(), client);
        } else {
            throw new AuctionExceptions.InvalidPayloadException("Lệnh đấu giá không hợp lệ: " + message.getCommand());
        }
    }

    @SuppressWarnings("unchecked")
    private void handlePlaceBid(Object data, ClientHandler client) throws Exception {
        User currentUser = client.getUser();

        // 1. CHẶN LỖI AUTHORIZATION TRÊN LUỒNG CHÍNH
        if (currentUser == null) {
            throw new AuctionExceptions.UnauthorizedAccessException("Bạn cần đăng nhập để đặt giá.");
        }

        Map<String, Object> bidData;
        try {
            bidData = (Map<String, Object>) data;
        } catch (ClassCastException e) {
            // 2. CHẶN LỖI ĐỊNH DẠNG PAYLOAD
            throw new AuctionExceptions.InvalidPayloadException("Cấu trúc dữ liệu đặt giá bị sai.");
        }

        String auctionId = (String) bidData.get("auctionId");
        long amount;
        try {
            amount = Long.parseLong(bidData.get("bidAmount").toString());
        } catch (NumberFormatException e) {
            throw new AuctionExceptions.InvalidPayloadException("Số tiền đặt giá không hợp lệ.");
        }

        boolean isBot = bidData.containsKey("isAutoBid") && (boolean) bidData.get("isAutoBid");

        Auction auction = auctionDAO.getAuctionById(auctionId);
        if (auction == null) {
            throw new AuctionExceptions.AuctionClosedException("Phiên đấu giá không tồn tại trên hệ thống.");
        }

        // 3. LUỒNG BẤT ĐỒNG BỘ: Sử dụng ErrorPayload thay vì throw
        bidderCtrl.placeBidOnAuction(currentUser, auction, amount, isBot).thenAccept(success -> {
            if (success) {
                client.sendResponse("BID_SUCCESS", "Đặt giá thành công!");
            } else {
                // Trả về ErrorPayload vì ta không thể throw Exception xuyên qua CompletableFuture về Dispatcher được
                client.sendResponse("ERROR", new ErrorPayload("ERR_BID_006", "Đặt giá thất bại. Vui lòng kiểm tra số dư khả dụng hoặc phiên đấu giá đã đóng."));
            }
        }).exceptionally(ex -> {
            // Lỗi đứt gãy Database hoặc System trong lúc xử lý Async
            client.sendResponse("ERROR", new ErrorPayload("ERR_SYS_500", "Xảy ra lỗi hệ thống khi khớp lệnh."));
            return null;
        });
    }
}