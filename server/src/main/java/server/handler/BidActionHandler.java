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

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Xử lý tất cả các lệnh mạng liên quan đến đặt giá từ phía client.
 *
 * <h2>Các lệnh được đăng ký</h2>
 * <ul>
 *   <li>{@code PLACE_BID}     — Đặt giá thủ công một lần.</li>
 *   <li>{@code SETUP_AUTOBID} — Đăng ký, cập nhật, hoặc hủy cấu hình Auto-Bid.</li>
 * </ul>
 *
 * <h2>Schema payload cho SETUP_AUTOBID</h2>
 * <pre>
 * {
 *   "auctionId" : "AUC-xxxxx",   // bắt buộc
 *   "maxBid"    : 5000000,       // bắt buộc (long, VNĐ); 0 = hủy Auto-Bid
 *   "increment" : 100000         // bắt buộc (long, VNĐ)
 * }
 * </pre>
 *
 * <h2>Các response command</h2>
 * <ul>
 *   <li>{@code BID_SUCCESS}         — Gửi đến client khi đặt giá thủ công thành công.</li>
 *   <li>{@code AUTOBID_SETUP_SUCCESS} — Gửi đến client khi đăng ký / hủy Auto-Bid thành công.</li>
 *   <li>{@code AUTOBID_ACTIVE}       — Broadcast đến tất cả clients theo dõi phiên khi Bot kích hoạt.</li>
 *   <li>{@code ERROR}                — {@link ErrorPayload} chuẩn hóa cho mọi trường hợp thất bại.</li>
 * </ul>
 */
public class BidActionHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(BidActionHandler.class);

    // ── Error codes cho SETUP_AUTOBID ───────────────────────────────────────
    private static final String ERR_AUTOBID_FUNDS    = "ERR_BID_010";
    private static final String ERR_AUTOBID_CONFLICT = "ERR_BID_012";

    private final ServerBidderController bidderCtrl;
    private final AuctionDAO             auctionDAO;

    /**
     * Khởi tạo handler với các dependency được inject từ bên ngoài.
     *
     * @param bidderCtrl Tầng controller đóng gói toàn bộ business logic đặt giá / auto-bid.
     * @param auctionDAO DAO dùng để resolve auction theo ID trước khi uỷ quyền xử lý.
     */
    public BidActionHandler(ServerBidderController bidderCtrl, AuctionDAO auctionDAO) {
        this.bidderCtrl = bidderCtrl;
        this.auctionDAO = auctionDAO;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CommandHandler contract
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Điểm vào duy nhất — phân phối các lệnh đến handler tương ứng.
     *
     * @param message Tin nhắn mạng nhận được từ client.
     * @param client  Context kết nối của client đang gửi lệnh.
     * @throws Exception nếu lệnh không hợp lệ hoặc xảy ra lỗi nghiệp vụ không thể phục hồi.
     */
    @Override
    public void handle(NetworkMessage message, ClientHandler client) throws Exception {
        switch (message.getCommand()) {
            case "PLACE_BID"     -> handlePlaceBid(message.getData(), client);
            case "SETUP_AUTOBID" -> handleSetupAutoBid(message.getData(), client);
            default -> throw new AuctionExceptions.InvalidPayloadException(
                    "Lệnh đấu giá không hợp lệ: " + message.getCommand());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PLACE_BID
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xử lý lệnh {@code PLACE_BID}: parse payload, validate, và uỷ quyền cho controller.
     * Kết quả được gửi bất đồng bộ qua {@link ClientHandler#sendResponse}.
     *
     * @param data   Raw payload từ {@link NetworkMessage#getData()}.
     * @param client Context kết nối của client.
     * @throws Exception nếu payload không hợp lệ hoặc user chưa xác thực.
     */
    @SuppressWarnings("unchecked")
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

        // Fire-and-forget: kết quả được trả về qua sendResponse / ClientManager.
        // Không block luồng xử lý lệnh hiện tại.
        bidderCtrl.placeBidOnAuction(currentUser, auction, amount, isBot)
                .thenAccept(success -> {
                    if (success) {
                        client.sendResponse("BID_SUCCESS", "Đặt giá thành công!");
                    } else {
                        client.sendResponse("ERROR",
                                new ErrorPayload("ERR_BID_006",
                                        "Đặt giá thất bại. Kiểm tra số dư hoặc phiên đã đóng."));
                    }
                })
                .exceptionally(ex -> {
                    log.error("Async bid execution error for auction {}: {}",
                            auctionId, ex.getMessage(), ex);
                    client.sendResponse("ERROR",
                            new ErrorPayload("ERR_SYS_500",
                                    "Lỗi hệ thống khi khớp lệnh đặt giá."));
                    return null;
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SETUP_AUTOBID
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xử lý lệnh {@code SETUP_AUTOBID}: parse và validate payload phía server, sau đó
     * uỷ quyền cho {@link ServerBidderController#setupAutoBid} hoặc
     * {@link ServerBidderController#cancelAutoBid} tùy theo giá trị {@code maxBid}.
     *
     * <p>Quy tắc phân nhánh: {@code maxBid == 0} → hủy Auto-Bid;
     * {@code maxBid > 0} → đăng ký / nâng cấp Auto-Bid.</p>
     *
     * @param data   Raw payload từ {@link NetworkMessage#getData()}.
     * @param client Context kết nối của client.
     * @throws Exception nếu payload không hợp lệ hoặc user chưa xác thực.
     */
    @SuppressWarnings("unchecked")
    private void handleSetupAutoBid(Object data, ClientHandler client) throws Exception {
        User currentUser = requireAuthenticatedUser(client);

        Map<String, Object> payload = castPayload(data, "Cấu trúc payload SETUP_AUTOBID bị sai.");

        // ── Parse fields ─────────────────────────────────────────────────────
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

        // ── Server-side validation ────────────────────────────────────────────
        // (Client đã validate, nhưng server không bao giờ tin tưởng dữ liệu từ client.)
        if (maxBid < 0) {
            throw new AuctionExceptions.InvalidPayloadException("maxBid không được âm.");
        }
        if (maxBid > 0 && increment <= 0) {
            throw new AuctionExceptions.InvalidPayloadException(
                    "increment phải lớn hơn 0 khi đặt Auto Bid.");
        }

        Auction auction = requireAuction(auctionId);

        // ── Self-bid guard ────────────────────────────────────────────────────
        if (auction.getSeller() != null
                && auction.getSeller().getId().equals(currentUser.getId())) {
            throw new AuctionExceptions.UnauthorizedAccessException(
                    "Người bán không thể đặt Auto Bid cho phiên của chính mình.");
        }

        // ── Dispatch: hủy hay đăng ký? ────────────────────────────────────────
        if (maxBid == 0) {
            dispatchCancelAutoBid(currentUser, auction, auctionId, client);
        } else {
            dispatchSetupAutoBid(currentUser, auction, auctionId, maxBid, increment, client);
        }
    }

    /**
     * Uỷ quyền hủy Auto-Bid đến {@link ServerBidderController#cancelAutoBid}
     * và xử lý phản hồi bất đồng bộ.
     *
     * @param currentUser Người dùng yêu cầu hủy.
     * @param auction     Phiên đấu giá mục tiêu (đã resolve).
     * @param auctionId   ID phiên — dùng để build response payload.
     * @param client      Context kết nối để gửi response.
     */
    private void dispatchCancelAutoBid(
            User currentUser, Auction auction, String auctionId, ClientHandler client) {

        log.info("User {} cancelling auto-bid on auction {}",
                currentUser.getUserName(), auctionId);

        bidderCtrl.cancelAutoBid(currentUser, auction)
                .thenAccept(success -> {
                    if (success) {
                        client.sendResponse("AUTOBID_SETUP_SUCCESS",
                                buildAutoBidResponse(auctionId, 0, 0, false));
                    } else {
                        client.sendResponse("ERROR",
                                new ErrorPayload(ERR_AUTOBID_CONFLICT,
                                        "Không tìm thấy Auto Bid đang hoạt động để hủy."));
                    }
                })
                .exceptionally(ex -> {
                    log.error("Error cancelling auto-bid for user {} on auction {}: {}",
                            currentUser.getUserName(), auctionId, ex.getMessage(), ex);
                    client.sendResponse("ERROR",
                            new ErrorPayload("ERR_SYS_500", "Lỗi hệ thống khi hủy Auto Bid."));
                    return null;
                });
    }

    /**
     * Uỷ quyền đăng ký / nâng cấp Auto-Bid đến {@link ServerBidderController#setupAutoBid}
     * và xử lý phản hồi bất đồng bộ.
     *
     * @param currentUser   Người dùng đăng ký.
     * @param auction       Phiên đấu giá mục tiêu (đã resolve).
     * @param auctionId     ID phiên — dùng để build response payload.
     * @param maxBid        Mức giá tối đa đã validate ({@code > 0}).
     * @param increment     Bước tăng giá đã validate ({@code > 0}).
     * @param client        Context kết nối để gửi response.
     */
    private void dispatchSetupAutoBid(
            User currentUser, Auction auction, String auctionId,
            long maxBid, long increment, ClientHandler client) {

        log.info("User {} setting auto-bid maxBid={} incr={} on auction {}",
                currentUser.getUserName(), maxBid, increment, auctionId);

        bidderCtrl.setupAutoBid(currentUser, auction, maxBid, increment)
                .thenAccept(success -> {
                    if (success) {
                        client.sendResponse("AUTOBID_SETUP_SUCCESS",
                                buildAutoBidResponse(auctionId, maxBid, increment, true));
                    } else {
                        // setupAutoBid trả về false khi số dư không đủ
                        client.sendResponse("ERROR",
                                new ErrorPayload(ERR_AUTOBID_FUNDS,
                                        "Số dư khả dụng không đủ để đăng ký Auto Bid với mức tối đa "
                                                + maxBid + " VNĐ."));
                    }
                })
                .exceptionally(ex -> {
                    log.error("Error setting up auto-bid for user {} on auction {}: {}",
                            currentUser.getUserName(), auctionId, ex.getMessage(), ex);
                    client.sendResponse("ERROR",
                            new ErrorPayload("ERR_SYS_500", "Lỗi hệ thống khi đăng ký Auto Bid."));
                    return null;
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Đảm bảo rằng client đã đăng nhập; ném exception nếu chưa xác thực.
     *
     * @param client Context kết nối.
     * @return Đối tượng {@link User} đã được xác thực.
     * @throws AuctionExceptions.UnauthorizedAccessException nếu chưa đăng nhập.
     */
    private User requireAuthenticatedUser(ClientHandler client) {
        User user = client.getUser();
        if (user == null) {
            throw new AuctionExceptions.UnauthorizedAccessException(
                    "Bạn cần đăng nhập để thực hiện thao tác này.");
        }
        return user;
    }

    /**
     * Resolve phiên đấu giá theo ID và đảm bảo nó tồn tại.
     *
     * <h3>Xử lý ngoại lệ — hai catch riêng biệt thay vì một catch + instanceof</h3>
     * <p>
     * [BUG FIX]: Phiên bản cũ dùng {@code catch (Exception e)} rồi kiểm tra
     * {@code instanceof AuctionBaseException} trước {@code throw e}. Vì
     * {@link AuctionDAO#getAuctionById} khai báo {@code throws SQLException} (checked),
     * Java's <em>precise rethrow</em> phân tích rằng {@code throw e} tại đó có thể
     * lan truyền {@code SQLException} — gây lỗi compile "unreported exception
     * java.sql.SQLException" (BidActionHandler.java:242, col 70).
     * </p>
     * <p>
     * Giải pháp: tách thành hai catch clause độc lập:
     * <ol>
     *   <li>{@code catch (AuctionExceptions.AuctionBaseException e)} — re-throw an toàn
     *       vì tất cả subtype đều là {@link RuntimeException} (không cần khai báo throws).</li>
     *   <li>{@code catch (Exception e)} — bọc mọi exception còn lại (bao gồm
     *       {@code SQLException}) vào {@code AuctionClosedException} (RuntimeException).</li>
     * </ol>
     * </p>
     *
     * @param auctionId ID của phiên đấu giá cần resolve.
     * @return Đối tượng {@link Auction} hợp lệ.
     * @throws AuctionExceptions.AuctionClosedException nếu không tìm thấy hoặc lỗi DB.
     */
    private Auction requireAuction(String auctionId) {
        try {
            Auction auction = auctionDAO.getAuctionById(auctionId);
            if (auction == null) {
                throw new AuctionExceptions.AuctionClosedException(
                        "Phiên đấu giá '" + auctionId + "' không tồn tại.");
            }
            return auction;

        } catch (AuctionExceptions.AuctionBaseException e) {
            // Re-throw business exceptions nguyên vẹn — tất cả subtype đều là RuntimeException.
            // Cách này tránh hoàn toàn vấn đề "precise rethrow with checked exception".
            throw e;

        } catch (SQLException e) {
            // Bọc checked exception từ tầng DAO thành business exception
            // để tầng gọi không cần biết về chi tiết DB.
            log.error("Database error resolving auction '{}': {}", auctionId, e.getMessage(), e);
            throw new AuctionExceptions.AuctionClosedException(
                    "Không thể truy xuất phiên đấu giá: " + auctionId);

        } catch (Exception e) {
            // Lưới bắt cuối cùng cho mọi lỗi không lường trước
            log.error("Unexpected error resolving auction '{}': {}", auctionId, e.getMessage(), e);
            throw new AuctionExceptions.AuctionClosedException(
                    "Lỗi không xác định khi truy xuất phiên đấu giá: " + auctionId);
        }
    }

    /**
     * Ép kiểu raw {@code Object} sang {@code Map<String, Object>};
     * bọc {@link ClassCastException} thành business exception sạch.
     *
     * @param data         Dữ liệu raw từ payload.
     * @param errorMessage Thông báo lỗi nghiệp vụ hiển thị cho client.
     * @return Map đã được ép kiểu an toàn.
     * @throws AuctionExceptions.InvalidPayloadException nếu kiểu dữ liệu không khớp.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> castPayload(Object data, String errorMessage) {
        try {
            return (Map<String, Object>) data;
        } catch (ClassCastException e) {
            throw new AuctionExceptions.InvalidPayloadException(errorMessage);
        }
    }

    /**
     * Tạo payload phản hồi cho lệnh {@code AUTOBID_SETUP_SUCCESS}.
     *
     * @param auctionId ID phiên đấu giá bị ảnh hưởng.
     * @param maxBid    Mức giá tối đa đã đăng ký (0 nếu đây là hủy).
     * @param increment Bước tăng giá đã đăng ký (0 nếu đây là hủy).
     * @param isActive  {@code true} nếu Auto-Bid đang hoạt động; {@code false} nếu đã hủy.
     * @return {@link HashMap} đơn giản, an toàn để serialise thành JSON.
     */
    private Map<String, Object> buildAutoBidResponse(
            String auctionId, long maxBid, long increment, boolean isActive) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("auctionId", auctionId);
        resp.put("maxBid",    maxBid);
        resp.put("increment", increment);
        resp.put("isActive",  isActive);
        return resp;
    }
}