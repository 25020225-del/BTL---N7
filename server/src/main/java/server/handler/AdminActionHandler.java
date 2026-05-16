package server.handler;

import database.TransactionManager;
import database.dao.AuctionDAO;
import exception.AuctionExceptions;
import network.ErrorPayload;
import model.user.User;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ClientHandler;

public class AdminActionHandler implements CommandHandler {
    private static final Logger log = LoggerFactory.getLogger(AdminActionHandler.class);
    private final database.dao.UserDAO userDAO;
    private final controller.ServerAdminController adminCtrl;

    public AdminActionHandler(AuctionDAO auctionDAO, database.dao.UserDAO userDAO, controller.ServerAdminController adminCtrl) {
        this.userDAO = userDAO;
        this.adminCtrl = adminCtrl;
    }

    @Override
    public void handle(NetworkMessage message, ClientHandler client) throws Exception {
        String command = message.getCommand();
        User adminUser = client.getUser();

        // Chặn quyền truy cập ngay từ đầu
        if (adminUser == null || !adminUser.getRole().equalsIgnoreCase("ADMIN")) {
            throw new AuctionExceptions.UnauthorizedAccessException("Chỉ Quản trị viên mới được phép thực hiện lệnh này.");
        }

        model.user.Admin admin = new model.user.Admin(adminUser);

        if ("FETCH_USERS".equals(command)) {
            handleFetchUsers(client);
            return;
        } else if ("BLOCK_USER".equals(command)) {
            handleUserBlock(admin, message.getData().toString(), true, client);
            return;
        } else if ("UNBLOCK_USER".equals(command)) {
            handleUserBlock(admin, message.getData().toString(), false, client);
            return;
        }

        String auctionId = (String) message.getData();

        if ("APPROVE_AUCTION".equals(command)) {
            TransactionManager.submitTask(() -> adminCtrl.approveAuction(admin, auctionId))
                    .thenAccept(success -> {
                        if (success) {
                            client.sendResponse("ADMIN_ACTION_SUCCESS", "Đã duyệt phiên đấu giá.");
                        } else {
                            client.sendResponse("ERROR", new ErrorPayload("ERR_AUC_002", "Không tìm thấy phiên đấu giá hoặc duyệt thất bại."));
                        }
                    }).exceptionally(ex -> {
                        log.error("Async approval failed: {}", ex.getMessage());
                        client.sendResponse("ERROR", new ErrorPayload("ERR_SYS_500", "Lỗi máy chủ khi thực thi lệnh."));
                        return null;
                    });
        } else if ("REJECT_AUCTION".equals(command)) {
            TransactionManager.submitTask(() -> adminCtrl.rejectAuction(admin, auctionId))
                    .thenAccept(success -> {
                        if (success) {
                            client.sendResponse("ADMIN_ACTION_SUCCESS", "Đã từ chối phiên đấu giá.");
                        } else {
                            client.sendResponse("ERROR", new ErrorPayload("ERR_AUC_002", "Không tìm thấy phiên đấu giá hoặc từ chối thất bại."));
                        }
                    }).exceptionally(ex -> {
                        log.error("Async rejection failed: {}", ex.getMessage());
                        client.sendResponse("ERROR", new ErrorPayload("ERR_SYS_500", "Lỗi máy chủ khi thực thi lệnh."));
                        return null;
                    });
        } else {
            throw new AuctionExceptions.InvalidPayloadException("Lệnh Admin không hợp lệ.");
        }
    }

    private void handleFetchUsers(ClientHandler client) throws Exception {
        java.util.List<java.util.Map<String, Object>> users = userDAO.getAllUsers();
        client.sendResponse("FETCH_USERS_SUCCESS", users);
    }

    private void handleUserBlock(model.user.Admin admin, String userId, boolean block, ClientHandler client) {
        boolean success = block ? adminCtrl.blockUser(admin, userId) : adminCtrl.unblockUser(admin, userId);
        if (success) {
            String action = block ? "khóa" : "mở khóa";
            client.sendResponse("ADMIN_ACTION_SUCCESS", "Người dùng " + userId + " đã bị " + action);
        } else {
            client.sendResponse("ERROR", new ErrorPayload("ERR_DB_005", "Không thể cập nhật trạng thái người dùng."));
        }
    }
}