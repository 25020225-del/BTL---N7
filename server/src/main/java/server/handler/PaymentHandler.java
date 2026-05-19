package server.handler;

import controller.ServerPaymentController;
import database.dao.WalletDAO;
import exception.AuctionExceptions;
import model.user.User;
import network.ErrorPayload;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ClientHandler;
import service.PayPalService;
import service.VietQRService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PaymentHandler implements CommandHandler {
    private static final Logger log = LoggerFactory.getLogger(PaymentHandler.class);
    private final PayPalService payPalService;
    private final VietQRService vietQRService;
    private final ServerPaymentController paymentController;
    private final service.TOTPService totpService;
    private final WalletDAO walletDAO;
    private final Map<String, DepositInfo> pendingDeposits = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
    private static final long DEPOSIT_EXPIRATION_MS = 15 * 60 * 1000L; // 15 minutes

    public PaymentHandler(ServerPaymentController paymentController,
                          service.TOTPService totpService,
                          WalletDAO walletDAO) {
        this.payPalService = new PayPalService();
        this.vietQRService = new VietQRService();
        this.paymentController = paymentController;
        this.totpService = totpService;
        this.walletDAO = walletDAO;
        startCleanupTask();
    }


    private void startCleanupTask() {
        // (Phần cleanup task giữ nguyên logic cũ vì nó chạy ngầm độc lập với Dispatcher)
        cleanupScheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();

            for (Map.Entry<String, DepositInfo> entry : pendingDeposits.entrySet()) {
                String orderId = entry.getKey();
                DepositInfo info = entry.getValue();

                if (now - info.getCreatedAt() > DEPOSIT_EXPIRATION_MS) {
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
            case "CREATE_VIETQR_DEPOSIT":
                handleCreateVietQRDeposit(data, client, currentUser);
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

    /**
     * Xử lý nạp tiền với cơ chế Challenge-Response TOTP (Stateless).
     *
     * <p><b>Giao thức payload từ Client:</b></p>
     * <pre>
     *   Lần 1 (không có TOTP): Map { "amount": 100000 }
     *   Lần 2 (có TOTP):       Map { "amount": 100000, "totpCode": "123456" }
     * </pre>
     *
     * <p><b>Logic server:</b></p>
     * <pre>
     *   isTotpPaymentEnabled = false → bỏ qua TOTP, xử lý luôn
     *   isTotpPaymentEnabled = true, totpCode rỗng → trả REQUIRE_TOTP_PAYMENT
     *   isTotpPaymentEnabled = true, totpCode có → validate → sai: INVALID_TOTP / đúng: xử lý
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private void handleCreateDeposit(Object data, ClientHandler client, User currentUser) throws Exception {
        // ── Parse payload ────────────────────────────────────────────────
        long amountVND;
        String totpCode = null; // null hoặc rỗng = chưa cung cấp

        try {
            if (data instanceof Map) {
                // Format mới: { "amount": 100000, "totpCode": "123456" (optional) }
                Map<String, Object> map = (Map<String, Object>) data;
                amountVND = Long.parseLong(map.get("amount").toString());
                Object codeObj = map.get("totpCode");
                if (codeObj != null && !codeObj.toString().isBlank()) {
                    totpCode = codeObj.toString().trim();
                }
            } else {
                // Legacy fallback: client cũ gửi raw number
                amountVND = Long.parseLong(data.toString());
            }
        } catch (Exception e) {
            throw new AuctionExceptions.InvalidPayloadException("Định dạng tiền tệ không hợp lệ.");
        }

        if (amountVND <= 0) {
            throw new AuctionExceptions.InvalidPayloadException("Số tiền nạp phải lớn hơn 0.");
        }

        // ── TOTP Challenge-Response ──────────────────────────────────────
        if (currentUser.isTotpPaymentEnabled()) {
            if (totpCode == null) {
                // Chưa cung cấp TOTP → thách thức client
                client.sendResponse("REQUIRE_TOTP_PAYMENT",
                        Map.of(
                                "message", "Giao dịch này yêu cầu xác thực TOTP. Vui lòng nhập mã 6 số.",
                                // Echo lại amount để client dùng khi retry
                                "amount", amountVND
                        ));
                log.info("TOTP challenge issued for deposit {} by user {}",
                        amountVND, currentUser.getUserName());
                return; // Dừng lại — không tạo order PayPal
            }

            // Validate TOTP code
            String secret = currentUser.getTotpSecret();
            if (secret == null) {
                // Trạng thái không nhất quán (hiếm gặp) — fail-safe
                client.sendResponse("ERROR",
                        new ErrorPayload("ERR_PAY_003",
                                "Lỗi cấu hình 2FA. Vui lòng tắt và bật lại TOTP trong Settings."));
                return;
            }

            int code;
            try {
                code = Integer.parseInt(totpCode);
            } catch (NumberFormatException e) {
                client.sendResponse("ERROR",
                        new ErrorPayload("ERR_PAY_004", "Mã TOTP không đúng định dạng (phải là 6 chữ số)."));
                return;
            }

            boolean valid = totpService.verifyCode(secret, code);
            if (!valid) {
                client.sendResponse("INVALID_TOTP",
                        Map.of("message", "Mã TOTP không hợp lệ hoặc đã hết hạn. Vui lòng thử lại."));
                log.warn("Invalid TOTP for payment by user {}", currentUser.getUserName());
                return; // Không trừ tiền, không tạo order
            }

            log.info("TOTP verified for payment by user {}", currentUser.getUserName());
        }

        // ── Tạo PayPal Order (GIỮ NGUYÊN logic cũ) ───────────────────────
        log.info("Creating deposit order of {} VND for {}", amountVND, currentUser.getUserName());
        String[] orderInfo = payPalService.createOrder(amountVND);
        String orderId = orderInfo[0];
        String approvalUrl = orderInfo[1];

        pendingDeposits.put(orderId,
                new DepositInfo(amountVND, System.currentTimeMillis(), client, currentUser));

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

        public long getAmountVND() {
            return amountVND;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public ClientHandler getClient() {
            return client;
        }

        public User getUser() {
            return user;
        }

        public AtomicBoolean getIsProcessing() {
            return isProcessing;
        }
    }

    /**
     * Handles the creation of a VietQR deposit request.
     * Generates a unique order ID, stores the context in RAM, and returns the QR string.
     *
     * @param data        The payload containing the deposit amount.
     * @param client      The active client session.
     * @param currentUser The authenticated user making the request.
     */
    @SuppressWarnings("unchecked")
    private void handleCreateVietQRDeposit(Object data, server.ClientHandler client, model.user.User currentUser) throws Exception {
        long amountVND;

        try {
            if (data instanceof java.util.Map) {
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) data;
                amountVND = Long.parseLong(map.get("amount").toString());
            } else {
                amountVND = Long.parseLong(data.toString());
            }
        } catch (Exception e) {
            throw new exception.AuctionExceptions.InvalidPayloadException("Định dạng số tiền không hợp lệ.");
        }

        if (amountVND <= 0) {
            throw new exception.AuctionExceptions.InvalidPayloadException("Số tiền nạp phải lớn hơn 0.");
        }

        String orderId = "VQR-" + System.currentTimeMillis();

        String qrString = vietQRService.generateVietQRString(amountVND, orderId);

        pendingDeposits.put(orderId, new DepositInfo(amountVND, System.currentTimeMillis(), client, currentUser));

        log.info("Created VietQR deposit order {} for user {}", orderId, currentUser.getUserName());

        java.util.Map<String, String> responseData = new java.util.HashMap<>();
        responseData.put("orderId", orderId);
        responseData.put("qrString", qrString);

        client.sendResponse("VIETQR_CREATED", responseData);
    }
}