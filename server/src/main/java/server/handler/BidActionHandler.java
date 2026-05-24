package server.handler;

import controller.ServerBidderController;
import database.dao.AuctionDAO;
import exception.AuctionExceptions;
import model.auction.Auction;
import model.user.User;
import network.ErrorPayload;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ClientHandler;
import server.ServerExtension.AuctionManager;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Structural endpoint command handler managing inbound transaction routing targeting
 * real-time bid placements and proxy agent lifecycle infrastructure modifications.
 */
public class BidActionHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(BidActionHandler.class);
    private static final String ERR_AUTOBID_FUNDS    = "ERR_BID_010";
    private static final String ERR_AUTOBID_MIN_BID  = "ERR_BID_011";
    private static final String ERR_AUTOBID_CONFLICT = "ERR_BID_012";

    private final ServerBidderController bidderCtrl;
    private final AuctionDAO auctionDAO;

    public BidActionHandler(ServerBidderController bidderCtrl, AuctionDAO auctionDAO) {
        this.bidderCtrl = bidderCtrl;
        this.auctionDAO = auctionDAO;
    }

    @Override
    public void handle(NetworkMessage message, ClientHandler client) throws Exception {
        switch (message.getCommand()) {
            case "PLACE_BID"     -> handlePlaceBid(message.getData(), client);
            case "SETUP_AUTOBID" -> handleSetupAutoBid(message.getData(), client);
            default -> throw new AuctionExceptions.InvalidPayloadException(
                    "Lệnh đấu giá không hợp lệ: " + message.getCommand());
        }
    }

    private void handlePlaceBid(Object data, ClientHandler client) throws Exception {
        User currentUser = requireAuthenticatedUser(client);
        Map<String, Object> bidData = castPayload(data, "Cấu trúc dữ liệu đặt giá bị sai.");

        String auctionId = (String) bidData.get("auctionId");
        long amount;
        try {
            amount = Long.parseLong(bidData.get("bidAmount").toString());
        } catch (NumberFormatException | NullPointerException e) {
            throw new AuctionExceptions.InvalidPayloadException("Số tiền đặt giá không hợp lệ.");
        }

        boolean isBot = bidData.containsKey("isAutoBid") && (boolean) bidData.get("isAutoBid");
        Auction auction = requireAuction(auctionId);

        bidderCtrl.placeBidOnAuction(currentUser, auction, amount, isBot)
                .thenAccept(success -> {
                    if (success) {
                        client.sendResponse("BID_SUCCESS", "Đặt giá thành công!");
                    } else {
                        client.sendResponse("ERROR", new ErrorPayload(
                                "ERR_BID_006", "Đặt giá thất bại. Kiểm tra số dư hoặc phiên đã đóng."));
                    }
                })
                .exceptionally(ex -> {
                    log.error("Async bid execution error for auction {}: {}", auctionId, ex.getMessage(), ex);
                    client.sendResponse("ERROR", new ErrorPayload(
                            "ERR_SYS_500", "Lỗi hệ thống khi khớp lệnh đặt giá."));
                    return null;
                });
    }

    private void handleSetupAutoBid(Object data, ClientHandler client) throws Exception {
        User currentUser = requireAuthenticatedUser(client);
        Map<String, Object> payload = castPayload(data, "Cấu trúc payload SETUP_AUTOBID bị sai.");

        String auctionId = (String) payload.get("auctionId");
        if (auctionId == null || auctionId.isBlank()) {
            throw new AuctionExceptions.InvalidPayloadException("Thiếu trường auctionId.");
        }

        long maxBid;
        long increment;
        try {
            maxBid    = Long.parseLong(payload.get("maxBid").toString());
            increment = Long.parseLong(payload.get("increment").toString());
        } catch (NumberFormatException | NullPointerException e) {
            throw new AuctionExceptions.InvalidPayloadException(
                    "maxBid và increment phải là số nguyên dương.");
        }

        if (maxBid < 0) {
            throw new AuctionExceptions.InvalidPayloadException("maxBid không được âm.");
        }
        if (maxBid > 0 && increment <= 0) {
            throw new AuctionExceptions.InvalidPayloadException(
                    "increment phải lớn hơn 0 khi đặt Auto Bid.");
        }

        Auction auction = requireAuction(auctionId);

        if (auction.getSeller() != null && auction.getSeller().getId().equals(currentUser.getId())) {
            throw new AuctionExceptions.UnauthorizedAccessException(
                    "Người bán không thể đặt Auto Bid cho phiên của chính mình.");
        }

        if (maxBid == 0) {
            dispatchCancelAutoBid(currentUser, auction, auctionId, client);
            return;
        }

        final long minRequired;

        // Acquired lock targets the static dynamic memory map layer to prevent state drift metrics calculation leaks
        // while foreign asynchronous inbound user frames challenge identical resource targets concurrently.
        synchronized (AuctionManager.getLockForAuction(auctionId)) {
            minRequired = auction.getMinAutoBidRequired();
        }

        if (maxBid < minRequired) {
            log.warn("[C4-GUARD] AutoBid rejected early: user={}, auction={}, maxBid={}, minRequired={}",
                    currentUser.getUserName(), auctionId, maxBid, minRequired);
            client.sendResponse("ERROR", new ErrorPayload(
                    ERR_AUTOBID_MIN_BID,
                    "Giá Autobid tối đa phải lớn hơn hoặc bằng " + minRequired + " VNĐ."));
            return;
        }

        dispatchSetupAutoBid(currentUser, auction, auctionId, maxBid, increment, client);
    }

    private void dispatchCancelAutoBid(User currentUser, Auction auction,
                                       String auctionId, ClientHandler client) {
        log.info("User {} cancelling auto-bid on auction {}", currentUser.getUserName(), auctionId);

        bidderCtrl.cancelAutoBid(currentUser, auction)
                .thenAccept(success -> {
                    if (success) {
                        client.sendResponse("AUTOBID_SETUP_SUCCESS",
                                buildAutoBidResponse(auctionId, 0, 0, false));
                    } else {
                        client.sendResponse("ERROR", new ErrorPayload(
                                ERR_AUTOBID_CONFLICT,
                                "Không tìm thấy Auto Bid đang hoạt động để hủy."));
                    }
                })
                .exceptionally(ex -> {
                    log.error("Error cancelling auto-bid for user {} on auction {}: {}",
                            currentUser.getUserName(), auctionId, ex.getMessage(), ex);
                    client.sendResponse("ERROR", new ErrorPayload(
                            "ERR_SYS_500", "Lỗi hệ thống khi hủy Auto Bid."));
                    return null;
                });
    }

    private void dispatchSetupAutoBid(User currentUser, Auction auction, String auctionId,
                                      long maxBid, long increment, ClientHandler client) {
        log.info("User {} setting auto-bid maxBid={} incr={} on auction {}",
                currentUser.getUserName(), maxBid, increment, auctionId);

        bidderCtrl.setupAutoBid(currentUser, auction, maxBid, increment)
                .thenAccept(success -> {
                    if (success) {
                        client.sendResponse("AUTOBID_SETUP_SUCCESS",
                                buildAutoBidResponse(auctionId, maxBid, increment, true));
                    } else {
                        client.sendResponse("ERROR", new ErrorPayload(
                                ERR_AUTOBID_FUNDS,
                                "Số dư khả dụng không đủ để đăng ký Auto Bid với mức tối đa "
                                        + maxBid + " VNĐ."));
                    }
                })
                .exceptionally(ex -> {
                    log.error("Error setting up auto-bid for user {} on auction {}: {}",
                            currentUser.getUserName(), auctionId, ex.getMessage(), ex);
                    client.sendResponse("ERROR", new ErrorPayload(
                            "ERR_SYS_500", "Lỗi hệ thống khi đăng ký Auto Bid."));
                    return null;
                });
    }

    private User requireAuthenticatedUser(ClientHandler client) {
        User user = client.getUser();
        if (user == null) {
            throw new AuctionExceptions.UnauthorizedAccessException(
                    "Bạn cần đăng nhập để thực hiện thao tác này.");
        }
        return user;
    }

    private Auction requireAuction(String auctionId) {
        try {
            Auction auction = auctionDAO.getAuctionById(auctionId);
            if (auction == null) {
                throw new AuctionExceptions.AuctionClosedException(
                        "Phiên đấu giá '" + auctionId + "' không tồn tại.");
            }
            return auction;

        } catch (AuctionExceptions.AuctionBaseException e) {
            throw e;

        } catch (SQLException e) {
            log.error("Database error resolving auction '{}': {}", auctionId, e.getMessage(), e);
            throw new AuctionExceptions.AuctionClosedException(
                    "Không thể truy xuất phiên đấu giá: " + auctionId);

        } catch (Exception e) {
            log.error("Unexpected error resolving auction '{}': {}", auctionId, e.getMessage(), e);
            throw new AuctionExceptions.AuctionClosedException(
                    "Lỗi không xác định khi truy xuất phiên đấu giá: " + auctionId);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castPayload(Object data, String errorMessage) {
        try {
            return (Map<String, Object>) data;
        } catch (ClassCastException e) {
            throw new AuctionExceptions.InvalidPayloadException(errorMessage);
        }
    }

    private Map<String, Object> buildAutoBidResponse(String auctionId, long maxBid,
                                                     long increment, boolean isActive) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("auctionId", auctionId);
        resp.put("maxBid",    maxBid);
        resp.put("increment", increment);
        resp.put("isActive",  isActive);
        return resp;
    }
}