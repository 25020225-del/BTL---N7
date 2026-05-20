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

import java.util.List;
import java.util.Map;

/**
 * Xử lý tất cả các lệnh quản trị (Admin-only) gửi từ phía Client.
 *
 * <p><b>Danh sách lệnh được xử lý:</b></p>
 * <pre>
 *   ── Quản lý người dùng ──────────────────────────────
 *   FETCH_USERS        → Lấy danh sách tất cả người dùng.
 *   BLOCK_USER         → Khóa tài khoản người dùng.
 *   UNBLOCK_USER       → Mở khóa tài khoản người dùng.
 *
 *   ── Quản lý phiên đấu giá ───────────────────────────
 *   APPROVE_AUCTION    → Duyệt phiên đấu giá đang PENDING.
 *   REJECT_AUCTION     → Từ chối phiên đấu giá đang PENDING.
 *
 *   ── Quản lý rút tiền [NEW] ───────────────────────────
 *   FETCH_WITHDRAW_REQUESTS → Lấy danh sách yêu cầu rút tiền PENDING.
 *   APPROVE_WITHDRAW        → Duyệt yêu cầu rút tiền (COMPLETED).
 *   REJECT_WITHDRAW         → Từ chối yêu cầu rút tiền (REJECTED + hoàn tiền).
 * </pre>
 *
 * <p><b>Bảo mật:</b> Mọi lệnh đều yêu cầu role = "ADMIN"; kiểm tra ngay
 * tại đầu phương thức {@link #handle} trước khi gọi bất kỳ logic nào.</p>
 */
public class AdminActionHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminActionHandler.class);

    private final database.dao.UserDAO userDAO;
    private final ServerAdminController adminCtrl;

    /**
     * Constructs the handler with required dependencies.
     *
     * @param auctionDAO DAO đấu giá (không dùng trực tiếp ở đây — được dùng trong adminCtrl).
     * @param userDAO    DAO người dùng (dùng cho FETCH_USERS).
     * @param adminCtrl  Controller Admin chứa toàn bộ business logic.
     */
    public AdminActionHandler(AuctionDAO auctionDAO,
                              database.dao.UserDAO userDAO,
                              ServerAdminController adminCtrl) {
        this.userDAO   = userDAO;
        this.adminCtrl = adminCtrl;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DISPATCHER CHÍNH
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void handle(NetworkMessage message, ClientHandler client) throws Exception {
        String command   = message.getCommand();
        User adminUser   = client.getUser();

        // ── GUARD: Chặn quyền truy cập ngay từ đầu ──────────────────────────
        if (adminUser == null || !adminUser.getRole().equalsIgnoreCase("ADMIN")) {
            throw new AuctionExceptions.UnauthorizedAccessException(
                    "Chỉ Quản trị viên mới được phép thực hiện lệnh này.");
        }

        Admin admin = new Admin(adminUser);

        switch (command) {

            // ── Quản lý người dùng ───────────────────────────────────────────
            case "FETCH_USERS"   -> handleFetchUsers(client);
            case "BLOCK_USER"    -> handleUserBlock(admin, message.getData().toString(), true, client);
            case "UNBLOCK_USER"  -> handleUserBlock(admin, message.getData().toString(), false, client);

            // ── Quản lý phiên đấu giá ────────────────────────────────────────
            case "APPROVE_AUCTION" -> handleAuctionAction(admin, (String) message.getData(), true, client);
            case "REJECT_AUCTION"  -> handleAuctionAction(admin, (String) message.getData(), false, client);

            // ── Quản lý rút tiền [NEW] ───────────────────────────────────────
            case "FETCH_WITHDRAW_REQUESTS" -> handleFetchWithdrawRequests(admin, client);
            case "APPROVE_WITHDRAW"        -> handleWithdrawAction(admin, message.getData(), true,  client);
            case "REJECT_WITHDRAW"         -> handleWithdrawAction(admin, message.getData(), false, client);

            default -> throw new AuctionExceptions.InvalidPayloadException(
                    "Lệnh Admin không hợp lệ: " + command);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HANDLERS — Quản lý người dùng (giữ nguyên)
    // ─────────────────────────────────────────────────────────────────────────

    private void handleFetchUsers(ClientHandler client) throws Exception {
        List<Map<String, Object>> users = userDAO.getAllUsers();
        client.sendResponse("FETCH_USERS_SUCCESS", users);
    }

    private void handleUserBlock(Admin admin, String userId, boolean block, ClientHandler client) {
        boolean success = block
                ? adminCtrl.blockUser(admin, userId)
                : adminCtrl.unblockUser(admin, userId);

        if (success) {
            String action = block ? "khóa" : "mở khóa";
            client.sendResponse("ADMIN_ACTION_SUCCESS",
                    "Người dùng " + userId + " đã bị " + action);
        } else {
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_DB_005", "Không thể cập nhật trạng thái người dùng."));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HANDLERS — Quản lý phiên đấu giá (tái cấu trúc cho gọn)
    // ─────────────────────────────────────────────────────────────────────────

    private void handleAuctionAction(Admin admin, String auctionId,
                                     boolean isApprove, ClientHandler client) {
        TransactionManager.submitTask(() ->
                isApprove
                        ? adminCtrl.approveAuction(admin, auctionId)
                        : adminCtrl.rejectAuction(admin, auctionId)
        ).thenAccept(success -> {
            if (success) {
                String msg = isApprove ? "Đã duyệt phiên đấu giá." : "Đã từ chối phiên đấu giá.";
                client.sendResponse("ADMIN_ACTION_SUCCESS", msg);
            } else {
                client.sendResponse("ERROR", new ErrorPayload("ERR_AUC_002",
                        "Không tìm thấy phiên đấu giá hoặc thao tác thất bại."));
            }
        }).exceptionally(ex -> {
            log.error("Async auction action failed: {}", ex.getMessage(), ex);
            client.sendResponse("ERROR",
                    new ErrorPayload("ERR_SYS_500", "Lỗi máy chủ khi thực thi lệnh."));
            return null;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HANDLERS — Quản lý rút tiền [NEW]
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches and returns the list of all PENDING withdrawal requests.
     *
     * <p>The DB read is submitted to {@link TransactionManager} to respect the
     * single-writer serialization invariant and avoid connection contention in
     * SQLite WAL mode under concurrent admin sessions.</p>
     *
     * <p>Response command: {@code FETCH_WITHDRAW_REQUESTS_SUCCESS}</p>
     *
     * @param admin  The admin performing the fetch.
     * @param client The admin's ClientHandler for sending the response.
     */
    private void handleFetchWithdrawRequests(Admin admin, ClientHandler client) {
        database.TransactionManager.submitTask(() -> {
            try {
                return adminCtrl.fetchPendingWithdrawals(admin);
            } catch (SecurityException e) {
                // Propagate as RuntimeException so exceptionally() can catch it.
                throw new RuntimeException("UNAUTHORIZED", e);
            }
        }).thenAccept(requests -> {
            client.sendResponse("FETCH_WITHDRAW_REQUESTS_SUCCESS", requests);
            log.info("[WITHDRAW] Admin {} fetched {} pending withdrawal request(s).",
                    admin.getUserName(), requests.size());
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause != null && "UNAUTHORIZED".equals(cause.getMessage())) {
                client.sendResponse("ERROR", new network.ErrorPayload(
                        "ERR_AUTH_403",
                        "Bạn không có quyền xem danh sách yêu cầu rút tiền."));
            } else {
                log.error("Error fetching pending withdrawals: {}", ex.getMessage(), ex);
                client.sendResponse("ERROR", new network.ErrorPayload(
                        "ERR_SYS_500",
                        "Lỗi hệ thống khi tải danh sách yêu cầu rút tiền."));
            }
            return null;
        });
    }

    /**
     * Xử lý lệnh Admin APPROVE hoặc REJECT một yêu cầu rút tiền.
     *
     * <p><b>Payload từ Client:</b> {@code String requestId} (ID của yêu cầu rút tiền)</p>
     *
     * <p><b>Response commands:</b></p>
     * <ul>
     *   <li>{@code WITHDRAW_ACTION_SUCCESS} — Thành công, kèm message mô tả hành động.</li>
     *   <li>{@code ERROR} — Thất bại kèm {@link ErrorPayload} mô tả lỗi.</li>
     * </ul>
     *
     * <p>Sau khi xử lý thành công, {@link ServerAdminController#processWithdrawal}
     * sẽ tự động gửi thông báo real-time cho User qua {@code ClientManager.sendToUser}
     * (nếu User đang online).</p>
     *
     * @param admin      Admin đang thực hiện hành động.
     * @param data       Payload từ client — phải là String requestId.
     * @param isApproved {@code true} = APPROVE, {@code false} = REJECT.
     * @param client     ClientHandler của Admin.
     */
    private void handleWithdrawAction(Admin admin, Object data,
                                      boolean isApproved, ClientHandler client) {
        // ── Parse requestId ──────────────────────────────────────────────────
        String requestId;
        try {
            requestId = data.toString().trim();
            if (requestId.isBlank()) {
                throw new IllegalArgumentException("requestId is blank");
            }
        } catch (Exception e) {
            client.sendResponse("ERROR", new ErrorPayload("ERR_PAY_020",
                    "ID yêu cầu rút tiền không hợp lệ."));
            return;
        }

        String actionLabel = isApproved ? "APPROVE" : "REJECT";
        log.info("[WITHDRAW-{}] Admin {} processing request: {}",
                actionLabel, admin.getUserName(), requestId);

        // ── Uỷ thác cho Controller (chạy bất đồng bộ qua TransactionManager) ─
        adminCtrl.processWithdrawal(admin, requestId, isApproved)
                .thenAccept(result -> {
                    switch (result) {
                        case "SUCCESS" -> {
                            String successMsg = isApproved
                                    ? "Đã duyệt yêu cầu rút tiền. Số tiền đã được chuyển ra khỏi hệ thống."
                                    : "Đã từ chối yêu cầu rút tiền. Số tiền đã được hoàn lại cho User.";
                            client.sendResponse("WITHDRAW_ACTION_SUCCESS",
                                    Map.of("requestId", requestId, "message", successMsg));
                        }
                        case "NOT_FOUND" -> client.sendResponse("ERROR",
                                new ErrorPayload("ERR_PAY_021",
                                        "Yêu cầu rút tiền không tồn tại hoặc đã được xử lý trước đó. "
                                                + "Vui lòng làm mới danh sách."));
                        case "WALLET_ERROR" -> client.sendResponse("ERROR",
                                new ErrorPayload("ERR_PAY_022",
                                        "Lỗi thao tác ví (số dư không đủ hoặc dữ liệu không nhất quán). "
                                                + "Vui lòng liên hệ kỹ thuật viên."));
                        case "UNAUTHORIZED" -> client.sendResponse("ERROR",
                                new ErrorPayload("ERR_AUTH_403",
                                        "Bạn không có quyền thực hiện thao tác này."));
                        default -> client.sendResponse("ERROR",
                                new ErrorPayload("ERR_DB_005",
                                        "Lỗi cơ sở dữ liệu khi xử lý yêu cầu rút tiền. Vui lòng thử lại."));
                    }
                }).exceptionally(ex -> {
                    log.error("[WITHDRAW-{}] Unexpected async error for request {}: {}",
                            actionLabel, requestId, ex.getMessage(), ex);
                    client.sendResponse("ERROR",
                            new ErrorPayload("ERR_SYS_500",
                                    "Lỗi máy chủ nghiêm trọng. Vui lòng thử lại sau."));
                    return null;
                });
    }
}