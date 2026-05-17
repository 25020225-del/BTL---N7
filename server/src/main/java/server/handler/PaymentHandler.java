package server.handler;

import database.dao.WalletDAO;
import exception.AuctionExceptions;
import network.ErrorPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import controller.ServerPaymentController;
import model.user.User;
import network.NetworkMessage;
import server.ClientHandler;
import service.PayPalService;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static utils.ConsoleColors.*;

public class PaymentHandler implements CommandHandler {
    private static final Logger log = LoggerFactory.getLogger(PaymentHandler.class);
    private final WalletDAO walletDAO = new WalletDAO();
    private final PayPalService payPalService;
    private final ServerPaymentController paymentController;
    private final Map<String, DepositInfo> pendingDeposits = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
    private static final long EXPIRATION_TIME_MS = 15 * 60 * 1000;

    public PaymentHandler(ServerPaymentController paymentController) {
        this.payPalService = new PayPalService();
        this.paymentController = paymentController;
        startCleanupTask();
    }

    private void startCleanupTask() {
        // (Phần cleanup task giữ nguyên logic cũ vì nó chạy ngầm độc lập với Dispatcher)
        cleanupScheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();

            for (Map.Entry<String, DepositInfo> entry : pendingDeposits.entrySet()) {
                String orderId = entry.getKey();
                DepositInfo info = entry.getValue();

                if (now - info.getCreatedAt() > EXPIRATION_TIME_MS) {
                    pendingDeposits.remove(orderId);
                    log.info("Removed expired pending deposit: {}", orderId);
                    continue;
                }

                if (info.getIsProcessing().compareAndSet(false, true)) {
                    try {
                        String status = payPalService.getOrderStatus(orderId);
                        if ("APPROVED".equals(status)) {
                            boolean isCaptured = payPalService.captureOrder(orderId);
                            if (isCaptured) {
                                long verifiedAmount = payPalService.getCapturedAmountVND(orderId);
                                paymentController.processDepositSuccess(info.getUser(), orderId, verifiedAmount).thenAccept(dbSuccess -> {
                                    if (dbSuccess) {
                                        pendingDeposits.remove(orderId);
                                        info.getClient().sendResponse("DEPOSIT_SUCCESS", "Automatic payment successful. Balance updated.");
                                    } else {
                                        info.getIsProcessing().set(false);
                                    }
                                });
                            } else {
                                info.getIsProcessing().set(false);
                            }
                        } else {
                            info.getIsProcessing().set(false);
                        }
                    } catch (Exception e) {
                        log.warn("Error polling PayPal order {}: {}", orderId, e.getMessage());
                        info.getIsProcessing().set(false);
                    }
                }
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    public void handle(NetworkMessage message, ClientHandler client) throws Exception {
        String command = message.getCommand();
        Object data = message.getData();
        User currentUser = client.getUser();

        // [ARCHITECT FIX]: Bắn ngoại lệ Authorization ngay từ vòng gửi xe
        if (currentUser == null) {
            throw new AuctionExceptions.UnauthorizedAccessException("Bạn cần đăng nhập để thực hiện giao dịch này.");
        }

        // Không cần try-catch (Exception e) "tạp nham" ở đây nữa. Dispatcher sẽ lo việc đó!
        switch (command) {
            case "CREATE_DEPOSIT":
                handleCreateDeposit(data, client, currentUser);
                break;
            case "CONFIRM_DEPOSIT":
                handleConfirmDeposit(data, client, currentUser);
                break;
            case "FETCH_WALLET":
                handleFetchWallet(client, currentUser);
                break;
            default:
                throw new AuctionExceptions.InvalidPayloadException("Lệnh thanh toán không hợp lệ: " + command);
        }
    }

    private void handleCreateDeposit(Object data, ClientHandler client, User currentUser) throws Exception {
        long amountVND;
        try {
            amountVND = Long.parseLong(data.toString());
        } catch (NumberFormatException e) {
            // [ARCHITECT FIX]: Ném ngoại lệ định dạng
            throw new AuctionExceptions.InvalidPayloadException("Định dạng tiền tệ không hợp lệ.");
        }

        if (amountVND <= 0) {
            // [ARCHITECT FIX]: Ném ngoại lệ logic kinh doanh
            throw new AuctionExceptions.InvalidPayloadException("Số tiền nạp phải lớn hơn 0.");
        }

        log.info("Creating deposit order of {} VND for {}", amountVND, currentUser.getUserName());
        String[] orderInfo = payPalService.createOrder(amountVND);
        String orderId = orderInfo[0];
        String approvalUrl = orderInfo[1];

        pendingDeposits.put(orderId, new DepositInfo(amountVND, System.currentTimeMillis(), client, currentUser));

        Map<String, String> responseData = new HashMap<>();
        responseData.put("orderId", orderId);
        responseData.put("url", approvalUrl);

        client.sendResponse("PAYMENT_REDIRECT", responseData);
    }

    private void handleConfirmDeposit(Object data, ClientHandler client, User currentUser) throws Exception {
        String orderId = data.toString().trim();
        DepositInfo depositInfo = pendingDeposits.get(orderId);

        if (depositInfo == null) {
            // [ARCHITECT FIX]: Đơn hàng không tồn tại
            throw new AuctionExceptions.InvalidPayloadException("Đơn hàng không tồn tại hoặc đã hết hạn (15 phút).");
        }

        if (!depositInfo.getUser().getId().equals(currentUser.getId())) {
            log.warn("Security Alert: IDOR attempt! User {} tried to claim deposit owned by User {}. Order ID: {}",
                    currentUser.getUserName(), depositInfo.getUser().getUserName(), orderId);
            // [ARCHITECT FIX]: Phát hiện hack IDOR -> Quăng ngay lỗi Unauthorized
            throw new AuctionExceptions.UnauthorizedAccessException("Bạn không phải là chủ nhân của giao dịch này.");
        }

        if (!depositInfo.getIsProcessing().compareAndSet(false, true)) {
            throw new AuctionExceptions.InvalidPayloadException("Giao dịch đang được xử lý, vui lòng không gửi lại yêu cầu.");
        }

        try {
            boolean isCaptured = payPalService.captureOrder(orderId);
            if (isCaptured) {
                long verifiedAmount = payPalService.getCapturedAmountVND(orderId);

                // LƯU Ý: Khối thenAccept chạy trên THREAD KHÁC. Không thể 'throw' ở trong này được.
                paymentController.processDepositSuccess(depositInfo.getUser(), orderId, verifiedAmount).thenAccept(dbSuccess -> {
                    if (dbSuccess) {
                        pendingDeposits.remove(orderId);
                        client.sendResponse("DEPOSIT_SUCCESS", "Thanh toán thành công. Số dư đã được cập nhật.");
                    } else {
                        depositInfo.getIsProcessing().set(false);
                        // Dùng ErrorPayload cho luồng bất đồng bộ
                        client.sendResponse("ERROR", new ErrorPayload("ERR_DB_005", "Xác thực thành công nhưng lỗi lưu database. Xin liên hệ Admin."));
                    }
                }).exceptionally(ex -> {
                    depositInfo.getIsProcessing().set(false);
                    client.sendResponse("ERROR", new ErrorPayload("ERR_SYS_500", "Lỗi đứt gãy luồng xử lý bất đồng bộ."));
                    return null;
                });
            } else {
                depositInfo.getIsProcessing().set(false);
                throw new AuctionExceptions.InvalidPayloadException("Giao dịch chưa được hoàn tất hoặc đã bị hủy trên PayPal.");
            }
        } catch (Exception e) {
            depositInfo.getIsProcessing().set(false);
            // Lỗi gọi API PayPal (Mất mạng, API sập,...) -> Ném ra cho Dispatcher bắt thành ERR_SYS_500
            throw e;
        }
    }

    private void handleFetchWallet(ClientHandler client, User currentUser) throws Exception {
        // [ARCHITECT FIX]: Xóa khối try-catch SQLException rườm rà.
        // Ném thẳng Exception ra ngoài. Dispatcher sẽ log lỗi ra console và gửi ErrorPayload "Lỗi hệ thống máy chủ nội bộ" cho Client để giấu chi tiết kĩ thuật DB.
        Map<String, Object> walletData = walletDAO.getWalletData(currentUser.getId());
        client.sendResponse("FETCH_WALLET_SUCCESS", walletData);
    }

    private static class DepositInfo {
        private final long amountVND;
        private final long createdAt;
        private final ClientHandler client;
        private final User user;
        private final AtomicBoolean isProcessing = new AtomicBoolean(false);

        public DepositInfo(long amountVND, long createdAt, ClientHandler client, User user) {
            this.amountVND = amountVND;
            this.createdAt = createdAt;
            this.client = client;
            this.user = user;
        }

        public long getAmountVND() { return amountVND; }
        public long getCreatedAt() { return createdAt; }
        public ClientHandler getClient() { return client; }
        public User getUser() { return user; }
        public AtomicBoolean getIsProcessing() { return isProcessing; }
    }
}