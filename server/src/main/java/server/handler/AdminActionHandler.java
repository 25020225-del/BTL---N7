package server.handler;

import controller.ServerAdminController;
import database.TransactionManager;
import database.dao.AuctionDAO;
import exception.AuctionExceptions;
import model.user.Admin;
import model.user.User;
import network.ErrorPayload;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ClientHandler;
import server.ServerExtension.ClientManager;
import service.AdminAuctionService;

import java.util.List;
import java.util.Map;

/**
 * Privileged security gateway ingress controller routing high-level
 * administrative actions including enforcement, session evaluation, and financial checks.
 */
public class AdminActionHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminActionHandler.class);

    private final database.dao.UserDAO userDAO;
    private final ServerAdminController adminCtrl;
    private final AdminAuctionService adminAuctionService;

    private static final String ERR_CANCEL_NOT_FOUND = "ERR_ADMIN_010";
    private static final String ERR_CANCEL_NOT_CANCELLABLE = "ERR_ADMIN_011";
    private static final String ERR_CANCEL_DB_ERROR = "ERR_ADMIN_012";
    private static final String ERR_CANCEL_INVALID_PAYLOAD = "ERR_ADMIN_013";

    public AdminActionHandler(AuctionDAO auctionDAO,
                              database.dao.UserDAO userDAO,
                              ServerAdminController adminCtrl,
                              AdminAuctionService adminAuctionService) {
        this.userDAO = userDAO;
        this.adminCtrl = adminCtrl;
        this.adminAuctionService = adminAuctionService;
    }

    @Override
    public void handle(NetworkMessage message, ClientHandler client) throws Exception {
        String command = message.getCommand();
        User adminUser = client.getUser();

        if (adminUser == null || !adminUser.getRole().equalsIgnoreCase("ADMIN")) {
            throw new AuctionExceptions.UnauthorizedAccessException("Chỉ Quản trị viên mới được phép thực hiện lệnh này.");
        }

        Admin admin = new Admin(adminUser);
        String rawData = parseStringSafe(message.getData());

        switch (command) {
            case "FETCH_USERS" -> handleFetchUsers(client);
            case "BLOCK_USER" -> {
                if (rawData.isBlank()) {
                    client.sendResponse("ERROR", new ErrorPayload("ERR_DB_005", "User ID không được để trống."));
                } else {
                    handleUserBlock(admin, rawData, true, client);
                }
            }
            case "UNBLOCK_USER" -> {
                if (rawData.isBlank()) {
                    client.sendResponse("ERROR", new ErrorPayload("ERR_DB_005", "User ID không được để trống."));
                } else {
                    handleUserBlock(admin, rawData, false, client);
                }
            }
            case "TOGGLE_GOOD_STATUS" -> handleToggleGoodStatus(admin, message.getData(), client);
            case "APPROVE_AUCTION" -> {
                if (rawData.isBlank()) {
                    client.sendResponse("ERROR", new ErrorPayload("ERR_AUC_002", "Auction ID không được để trống."));
                } else {
                    handleAuctionAction(admin, rawData, true, client);
                }
            }
            case "REJECT_AUCTION" -> {
                if (rawData.isBlank()) {
                    client.sendResponse("ERROR", new ErrorPayload("ERR_AUC_002", "Auction ID không được để trống."));
                } else {
                    handleAuctionAction(admin, rawData, false, client);
                }
            }
            case "FETCH_WITHDRAW_REQUESTS" -> handleFetchWithdrawRequests(admin, client);
            case "APPROVE_WITHDRAW" -> handleWithdrawAction(admin, message.getData(), true, client);
            case "REJECT_WITHDRAW" -> handleWithdrawAction(admin, message.getData(), false, client);
            case "CANCEL_AUCTION" -> handleCancelAuction(admin, message.getData(), client);
            default -> throw new AuctionExceptions.InvalidPayloadException("Lệnh Admin không hợp lệ: " + command);
        }
    }

    private void handleToggleGoodStatus(Admin admin, Object data, ClientHandler client) {
        String targetUserId = parseStringSafe(data);
        if (targetUserId.isBlank()) {
            client.sendResponse("ERROR", new ErrorPayload("ERR_PAY_030", "User ID không hợp lệ hoặc bị thiếu trong payload."));
            return;
        }

        log.info("[TOGGLE_GOOD] Admin '{}' requested toggle for user '{}'.", admin.getUserName(), targetUserId);

        TransactionManager.submitTask(() -> adminCtrl.toggleGoodStatus(admin, targetUserId))
                .thenAccept(result -> {
                    if (result == null) {
                        client.sendResponse("ERROR", new ErrorPayload("ERR_SYS_500", "Lỗi máy chủ không xác định."));
                        return;
                    }

                    switch (result.getStatus()) {
                        case SUCCESS -> {
                            Map<String, Object> payload = Map.of(
                                    "userId", result.getUserId(),
                                    "isGood", result.getNewIsGood(),
                                    "message", result.getMessage()
                            );
                            client.sendResponse("TOGGLE_GOOD_SUCCESS", payload);
                            log.info("[TOGGLE_GOOD] Success — user '{}' is_good → {}.", result.getUserId(), result.getNewIsGood());
                        }
                        case UNAUTHORIZED -> client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_403", result.getMessage()));
                        case DENIED -> client.sendResponse("ERROR", new ErrorPayload("ERR_USR_001", result.getMessage()));
                        case NOT_FOUND -> client.sendResponse("ERROR", new ErrorPayload("ERR_USR_404", result.getMessage()));
                        case DB_ERROR -> client.sendResponse("ERROR", new ErrorPayload("ERR_DB_005", result.getMessage()));
                    }
                })
                .exceptionally(ex -> {
                    log.error("[TOGGLE_GOOD] Unexpected async error for user '{}': {}", targetUserId, ex.getMessage(), ex);
                    client.sendResponse("ERROR", new ErrorPayload("ERR_SYS_500", "Lỗi máy chủ nghiêm trọng khi cập nhật trạng thái người dùng."));
                    return null;
                });
    }

    private void handleFetchUsers(ClientHandler client) throws Exception {
        List<Map<String, Object>> users = userDAO.getAllUsers();
        client.sendResponse("FETCH_USERS_SUCCESS", users);
    }

    private void handleUserBlock(Admin admin, String userId, boolean block, ClientHandler client) {
        boolean success = block ? adminCtrl.blockUser(admin, userId) : adminCtrl.unblockUser(admin, userId);

        if (!success) {
            client.sendResponse("ERROR", new ErrorPayload("ERR_DB_005", "Không thể cập nhật trạng thái người dùng. Kiểm lại quyền hoặc ID người dùng."));
            return;
        }

        ClientManager.updateBlockStatusInMemory(userId, block);

        if (block) {
            ClientManager.sendToUser(userId, "ACCOUNT_BLOCKED", Map.of(
                    "message", "Tài khoản của bạn đã bị Quản trị viên khóa. Mọi giao dịch đặt giá đã bị dừng ngay lập tức. Vui lòng liên hệ hỗ trợ để được giải đáp."
            ));
            log.info("[AUDIT] Admin '{}' (id={}) performed BLOCKED on user '{}'.", admin.getUserName(), admin.getId(), userId);
        } else {
            ClientManager.sendToUser(userId, "ACCOUNT_UNBLOCKED", Map.of(
                    "message", "Tài khoản của bạn đã được Quản trị viên mở khóa. Bạn có thể tiếp tục sử dụng hệ thống bình thường."
            ));
            log.info("[AUDIT] Admin '{}' (id={}) performed UNBLOCKED on user '{}'.", admin.getUserName(), admin.getId(), userId);
        }

        String actionLabel = block ? "khóa" : "mở khóa";
        client.sendResponse("ADMIN_ACTION_SUCCESS", Map.of(
                "userId", userId,
                "blocked", block,
                "message", "Tài khoản người dùng " + userId + " đã được " + actionLabel + " thành công."
        ));
    }

    private void handleAuctionAction(Admin admin, String auctionId, boolean isApprove, ClientHandler client) {
        TransactionManager.submitTask(() -> isApprove ? adminCtrl.approveAuction(admin, auctionId) : adminCtrl.rejectAuction(admin, auctionId))
                .thenAccept(success -> {
                    if (success) {
                        String msg = isApprove ? "Đã duyệt phiên đấu giá." : "Đã từ chối phiên đấu giá.";
                        client.sendResponse("ADMIN_ACTION_SUCCESS", msg);
                    } else {
                        client.sendResponse("ERROR", new ErrorPayload("ERR_AUC_002", "Không tìm thấy phiên đấu giá hoặc thao tác thất bại."));
                    }
                }).exceptionally(ex -> {
                    log.error("Async auction action failed: {}", ex.getMessage(), ex);
                    client.sendResponse("ERROR", new ErrorPayload("ERR_SYS_500", "Lỗi máy chủ khi thực thi lệnh."));
                    return null;
                });
    }

    private void handleFetchWithdrawRequests(Admin admin, ClientHandler client) {
        TransactionManager.submitTask(() -> {
            try {
                return adminCtrl.fetchPendingWithdrawals(admin);
            } catch (SecurityException e) {
                throw new RuntimeException("UNAUTHORIZED", e);
            }
        }).thenAccept(requests -> {
            client.sendResponse("FETCH_WITHDRAW_REQUESTS_SUCCESS", requests);
            log.info("[WITHDRAW] Admin {} fetched {} pending withdrawal request(s).", admin.getUserName(), requests.size());
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause != null && "UNAUTHORIZED".equals(cause.getMessage())) {
                client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_403", "Bạn không có quyền xem danh sách yêu cầu rút tiền."));
            } else {
                log.error("Error fetching pending withdrawals: {}", ex.getMessage(), ex);
                client.sendResponse("ERROR", new ErrorPayload("ERR_SYS_500", "Lỗi hệ thống khi tải danh sách yêu cầu rút tiền."));
            }
            return null;
        });
    }

    private void handleWithdrawAction(Admin admin, Object data, boolean isApproved, ClientHandler client) {
        String requestId = parseStringSafe(data);
        if (requestId.isBlank()) {
            client.sendResponse("ERROR", new ErrorPayload("ERR_PAY_020", "ID yêu cầu rút tiền không hợp lệ."));
            return;
        }

        String actionLabel = isApproved ? "APPROVE" : "REJECT";
        log.info("[WITHDRAW-{}] Admin {} processing request: {}", actionLabel, admin.getUserName(), requestId);

        adminCtrl.processWithdrawal(admin, requestId, isApproved)
                .thenAccept(result -> {
                    switch (result) {
                        case "SUCCESS" -> {
                            String successMsg = isApproved ? "Đã duyệt yêu cầu rút tiền. Số tiền đã được chuyển ra khỏi hệ thống." : "Đã từ chối yêu cầu rút tiền. Số tiền đã được hoàn lại cho User.";
                            client.sendResponse("WITHDRAW_ACTION_SUCCESS", Map.of("requestId", requestId, "message", successMsg));
                        }
                        case "NOT_FOUND" -> client.sendResponse("ERROR", new ErrorPayload("ERR_PAY_021", "Yêu cầu rút tiền không tồn tại hoặc đã được xử lý trước đó. Vui lòng làm mới danh sách."));
                        case "WALLET_ERROR" -> client.sendResponse("ERROR", new ErrorPayload("ERR_PAY_022", "Lỗi thao tác ví (số dư không đủ hoặc dữ liệu không nhất quán). Vui lòng liên hệ kỹ thuật viên."));
                        case "UNAUTHORIZED" -> client.sendResponse("ERROR", new ErrorPayload("ERR_AUTH_403", "Bạn không có quyền thực hiện thao tác này."));
                        default -> client.sendResponse("ERROR", new ErrorPayload("ERR_DB_005", "Lỗi cơ sở dữ liệu khi xử lý yêu cầu rút tiền. Vui lòng thử lại."));
                    }
                }).exceptionally(ex -> {
                    log.error("[WITHDRAW-{}] Unexpected async error for request {}: {}", actionLabel, requestId, ex.getMessage(), ex);
                    client.sendResponse("ERROR", new ErrorPayload("ERR_SYS_500", "Lỗi máy chủ nghiêm trọng. Vui lòng thử lại sau."));
                    return null;
                });
    }

    private void handleCancelAuction(Admin admin, Object data, ClientHandler client) {
        String auctionId = parseStringSafe(data);
        if (auctionId.isBlank()) {
            client.sendResponse("ERROR", new ErrorPayload(ERR_CANCEL_INVALID_PAYLOAD, "auctionId không được để trống."));
            return;
        }

        log.info("Admin {} requested CANCEL_AUCTION for: {}", admin.getUserName(), auctionId);
        final String finalAuctionId = auctionId.trim();

        adminAuctionService.cancelAuctionAndRefundAsync(finalAuctionId)
                .thenAccept(result -> {
                    switch (result) {
                        case SUCCESS -> client.sendResponse("CANCEL_AUCTION_SUCCESS", Map.of("auctionId", finalAuctionId, "message", "Phiên đấu giá đã được hủy thành công. Toàn bộ tiền đặt giá đã được hoàn về ví người dùng."));
                        case NOT_FOUND -> client.sendResponse("ERROR", new ErrorPayload(ERR_CANCEL_NOT_FOUND, "Không tìm thấy phiên đấu giá: " + finalAuctionId));
                        case NOT_CANCELLABLE -> client.sendResponse("ERROR", new ErrorPayload(ERR_CANCEL_NOT_CANCELLABLE, "Phiên đấu giá này không thể hủy. Phiên chỉ có thể hủy khi đang ở trạng thái OPEN, WAITING_FOR_BID hoặc RUNNING."));
                        case DB_ERROR -> client.sendResponse("ERROR", new ErrorPayload(ERR_CANCEL_DB_ERROR, "Lỗi cơ sở dữ liệu. Thao tác đã được hoàn tác (rollback). Vui lòng thử lại hoặc liên hệ kỹ thuật."));
                    }
                })
                .exceptionally(ex -> {
                    log.error("Unhandled exception in CANCEL_AUCTION for {}: {}", finalAuctionId, ex.getMessage(), ex);
                    client.sendResponse("ERROR", new ErrorPayload("ERR_SYS_500", "Lỗi hệ thống nghiêm trọng khi hủy phiên đấu giá."));
                    return null;
                });
    }

    private String parseStringSafe(Object val) {
        if (val == null) return "";
        return val.toString().trim();
    }
}