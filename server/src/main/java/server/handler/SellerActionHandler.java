package server.handler;

import controller.ServerSellerController;
import exception.AuctionExceptions;
import model.auction.Auction;
import model.user.User;
import network.ErrorPayload;
import network.NetworkMessage;
import server.ClientHandler;
import server.ServerExtension.AuctionManager;

import java.util.Map;

/**
 * Command route handler managing mutation operations on un-started auctions
 * owned exclusively by authenticated hosting vendors.
 */
public class SellerActionHandler implements CommandHandler {

    private final ServerSellerController sellerCtrl;
    private final database.dao.AuctionDAO auctionDAO;

    public SellerActionHandler(ServerSellerController sellerCtrl, database.dao.AuctionDAO auctionDAO) {
        this.sellerCtrl = sellerCtrl;
        this.auctionDAO = auctionDAO;
    }

    @Override
    public void handle(NetworkMessage message, ClientHandler client) throws Exception {
        User user = client.getUser();
        if (user == null) {
            throw new AuctionExceptions.UnauthorizedAccessException("Bạn phải đăng nhập để quản lý phiên đấu giá.");
        }

        String command = message.getCommand();

        if ("EDIT_AUCTION".equals(command)) {
            handleEdit(message.getData(), client);
        } else if ("DELETE_AUCTION".equals(command)) {
            handleDelete(message.getData(), client);
        } else {
            throw new AuctionExceptions.InvalidPayloadException("Lệnh người bán không hợp lệ.");
        }
    }

    @SuppressWarnings("unchecked")
    private void handleEdit(Object data, ClientHandler client) throws Exception {
        Map<String, Object> payload;
        try {
            payload = (Map<String, Object>) data;
        } catch (ClassCastException e) {
            throw new AuctionExceptions.InvalidPayloadException("Dữ liệu cập nhật không đúng định dạng.");
        }

        String auctionId = (String) payload.get("auctionId");
        Auction auction = auctionDAO.getAuctionById(auctionId);

        if (auction != null) {
            for (Auction ramAuction : AuctionManager.getAuctionList()) {
                if (ramAuction.getId().equals(auctionId)) {
                    auction = ramAuction;
                    break;
                }
            }
        }

        if (auction == null) {
            throw new AuctionExceptions.AuctionClosedException("Không tìm thấy phiên đấu giá trên hệ thống.");
        }

        String newName = payload.containsKey("itemName") ? (String) payload.get("itemName") : auction.getItem().getItemName();
        String newDesc = payload.containsKey("description") ? (String) payload.get("description") : auction.getItem().getDescription();
        long newStartPrice = payload.containsKey("startPrice") ? Long.parseLong(payload.get("startPrice").toString()) : auction.getItem().getStartingPrice();

        java.time.LocalDateTime newStartTime = auction.getStartTime();
        if (payload.containsKey("newStartTime")) {
            newStartTime = java.time.LocalDateTime.parse((String) payload.get("newStartTime"));
        }

        java.time.LocalDateTime newEndTime = auction.getEndTime();
        if (payload.containsKey("newEndTime")) {
            newEndTime = java.time.LocalDateTime.parse((String) payload.get("newEndTime"));
        }

        boolean success = sellerCtrl.editAuction(client.getUser(), auction, newName, newDesc, newStartPrice, newStartTime, newEndTime);

        if (success) {
            client.sendResponse("EDIT_SUCCESS", "Cập nhật phiên đấu giá thành công.");
        } else {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUC_002", "Cập nhật thất bại. Đảm bảo phiên đấu giá chưa bắt đầu và bạn là chủ sở hữu."));
        }
    }

    private void handleDelete(Object data, ClientHandler client) throws Exception {
        String auctionId = (String) data;
        Auction auction = auctionDAO.getAuctionById(auctionId);

        if (auction != null) {
            for (Auction ramAuction : AuctionManager.getAuctionList()) {
                if (ramAuction.getId().equals(auctionId)) {
                    auction = ramAuction;
                    break;
                }
            }
        }

        if (auction == null) {
            throw new AuctionExceptions.AuctionClosedException("Không tìm thấy phiên đấu giá trên hệ thống.");
        }

        boolean success = sellerCtrl.deleteAuction(client.getUser(), auction);

        if (success) {
            client.sendResponse("DELETE_SUCCESS", "Xóa phiên đấu giá thành công.");
        } else {
            client.sendResponse("ERROR", new ErrorPayload("ERR_AUC_002", "Xóa thất bại. Bạn không thể xóa phiên đang diễn ra hoặc đã kết thúc."));
        }
    }
}