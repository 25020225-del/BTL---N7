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
import service.TOTPService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Command route handler processing global monetary deposit transactions,
 * wallet data gathering, and multi-factor withdrawal accounting steps.
 */
public class PaymentHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentHandler.class);
    private static final long DEPOSIT_EXPIRATION_MS = 15 * 60 * 1000L;
    private static final long TOTP_REPLAY_WINDOW_MS = 90_000L;
    private static final long MAX_TRANSACTION_LIMIT = 100_000_000L; // 100 Million VND limit

    public static final Map<String, DepositInfo> pendingDeposits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, Long>> usedTotpCodes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "PaymentCleanup-Worker");
        t.setDaemon(true);
        return t;
    });

    // Virtual Thread Executor for decoupling I/O socket writes from DB pool worker threads
    private static final java.util.concurrent.ExecutorService networkExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final PayPalService payPalService;
    private final ServerPaymentController paymentController;
    private final TOTPService totpService;
    private final WalletDAO walletDAO;

    public PaymentHandler(ServerPaymentController paymentController, TOTPService totpService, WalletDAO walletDAO) {
        this.payPalService = new PayPalService();
        this.paymentController = paymentController;
        this.totpService = totpService;
        this.walletDAO = walletDAO;
        startCleanupTask();
    }

    /**
     * Safely parses monetary values and handles double/float casting from JSON requests,
     * while enforcing maximum trade value limits.
     */
    private long parseAmountVND(Object amountObj) throws AuctionExceptions.InvalidPayloadException {
        if (amountObj == null) {
            throw new AuctionExceptions.InvalidPayloadException("Số tiền không được để trống.");
        }
        try {
            double parsedDouble = Double.parseDouble(amountObj.toString().trim());
            if (Double.isNaN(parsedDouble) || Double.isInfinite(parsedDouble)) {
                throw new AuctionExceptions.InvalidPayloadException("Số tiền không hợp lệ.");
            }
            long amount = (long) parsedDouble;
            if (amount <= 0) {
                throw new AuctionExceptions.InvalidPayloadException("Số tiền phải lớn hơn 0 VND.");
            }
            if (amount > MAX_TRANSACTION_LIMIT) {
                throw new AuctionExceptions.InvalidPayloadException(
                        String.format("Số tiền giao dịch vượt quá hạn mức tối đa cho phép (%,d VND).", MAX_TRANSACTION_LIMIT)
                );
            }
            return amount;
        } catch (NumberFormatException e) {
            throw new AuctionExceptions.InvalidPayloadException("Số tiền giao dịch phải là định dạng số hợp lệ.");
        }
    }

    /**
     * Gracefully shuts down the cleanup daemon scheduler and virtual thread pools to prevent thread leaks.
     */
    public void shutdown() {
        log.info("Shutting down PaymentHandler clean-up task and virtual thread executors...");
        try {
            cleanupScheduler.shutdown();
            if (!cleanupScheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        networkExecutor.shutdown();
    }

    @Override
    public void handle(NetworkMessage message, ClientHandler client) throws Exception {
        String command = message.getCommand();
        Object data = message.getData();
        User currentUser = client.getUser();

        if (currentUser == null) {
            throw new AuctionExceptions.UnauthorizedAccessException("Bạn cần đăng nhập để thực hiện giao dịch này.");
        }

        switch (command) {
            case "CREATE_DEPOSIT" -> handleCreateDeposit(data, client, currentUser);
            case "CONFIRM_DEPOSIT" -> handleConfirmDeposit(data, client, currentUser);
            case "FETCH_WALLET" -> handleFetchWallet(client, currentUser);
            case "REQUEST_WITHDRAW" -> handleRequestWithdraw(data, client, currentUser);
            default -> throw new AuctionExceptions.InvalidPayloadException("Lệnh thanh toán không hợp lệ: " + command);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleRequestWithdraw(Object data, ClientHandler client, User currentUser) throws Exception {
        long amountVND;
        String payoutMethod;
        String payoutDetails;
        String totpCode = null;

        try {
            Map<String, Object> map = (Map<String, Object>) data;
            payoutMethod = (String) map.get("payoutMethod");
            payoutDetails = (String) map.get("payoutDetails");

            Object codeObj = map.get("totpCode");
            if (codeObj != null && !codeObj.toString().isBlank()) {
                totpCode = codeObj.toString().trim();
            }

            // Safely parse the monetary amount with float support and limit constraints
            amountVND = parseAmountVND(map.get("amount"));
        } catch (ClassCastException | NullPointerException e) {
            throw new AuctionExceptions.InvalidPayloadException("Dữ liệu yêu cầu rút tiền không đúng định dạng. Vui lòng kiểm tra lại amount, payoutMethod và payoutDetails.");
        }

        if (payoutMethod == null || payoutMethod.isBlank()) {
            throw new AuctionExceptions.InvalidPayloadException("Phương thức nhận tiền không được để trống.");
        }
        if (payoutDetails == null || payoutDetails.isBlank()) {
            throw new AuctionExceptions.InvalidPayloadException("Thông tin tài khoản nhận không được để trống.");
        }

        if (currentUser.isTotpPaymentEnabled()) {
            if (totpCode == null) {
                client.sendResponse("REQUIRE_TOTP_PAYMENT", Map.of(
                        "message", "Giao dịch rút tiền này yêu cầu xác thực TOTP. Vui lòng nhập mã 6 số từ ứng dụng Authenticator.",
                        "amount", amountVND,
                        "payoutMethod", payoutMethod,
                        "payoutDetails", payoutDetails
                ));
                log.info("[WITHDRAW] TOTP challenge issued for user {} (amount={})", currentUser.getUserName(), amountVND);
                return;
            }

            String secret = currentUser.getTotpSecret();
            if (secret == null) {
                client.sendResponse("ERROR", new ErrorPayload("ERR_PAY_003", "Lỗi cấu hình 2FA. Vui lòng tắt và bật lại TOTP trong Settings."));
                return;
            }

            int code;
            try {
                code = Integer.parseInt(totpCode);
            } catch (NumberFormatException e) {
                client.sendResponse("ERROR", new ErrorPayload("ERR_PAY_004", "Mã TOTP không đúng định dạng (phải là 6 chữ số)."));
                return;
            }

            if (!totpService.verifyCode(secret, code)) {
                client.sendResponse("INVALID_TOTP", Map.of("message", "Mã TOTP không hợp lệ hoặc đã hết hạn. Vui lòng thử lại."));
                log.warn("[WITHDRAW] Invalid TOTP for user {} (amount={})", currentUser.getUserName(), amountVND);
                return;
            }
            if (isReplayAndRecord(currentUser.getId(), code)) {
                client.sendResponse("INVALID_TOTP", Map.of("message", "Mã TOTP này đã được sử dụng. Vui lòng đợi mã mới xuất hiện trên ứng dụng Authenticator."));
                log.warn("[SECURITY] TOTP replay attempt detected for user {} (code={})", currentUser.getUserName(), code);
                return;
            }

            log.info("[WITHDRAW] TOTP verified for user {} (amount={})", currentUser.getUserName(), amountVND);
        }

        final long finalAmount = amountVND;
        final String finalMethod = payoutMethod;
        final String finalDetails = payoutDetails;

        // Perform async DB request and execute networking callbacks in isolated Virtual Threads
        paymentController.createWithdrawalRequest(currentUser, finalAmount, finalMethod, finalDetails)
                .thenAcceptAsync(result -> {
                    switch (result) {
                        case "SUCCESS" -> {
                            client.sendResponse("WITHDRAW_REQUEST_SUCCESS", Map.of(
                                    "message", "Yêu cầu rút " + finalAmount + " VND đã được ghi nhận và đang chờ Admin duyệt.",
                                    "amount", finalAmount
                            ));
                            log.info("[WITHDRAW] Request created successfully for user {} (amount={})", currentUser.getUserName(), finalAmount);
                        }
                        case "INSUFFICIENT_FUNDS" -> client.sendResponse("ERROR", new ErrorPayload("ERR_PAY_010", "Số dư khả dụng không đủ để thực hiện yêu cầu rút tiền này."));
                        case "INVALID_AMOUNT" -> client.sendResponse("ERROR", new ErrorPayload("ERR_PAY_011", "Số tiền rút không hợp lệ (phải lớn hơn 0)."));
                        default -> client.sendResponse("ERROR", new ErrorPayload("ERR_DB_005", "Lỗi hệ thống khi tạo yêu cầu rút tiền. Vui lòng thử lại hoặc liên hệ Admin."));
                    }
                }, networkExecutor).exceptionally(ex -> {
                    log.error("[WITHDRAW] Unexpected error in async chain for user {}: {}", currentUser.getUserName(), ex.getMessage(), ex);
                    client.sendResponse("ERROR", new ErrorPayload("ERR_SYS_500", "Lỗi đứt gãy luồng xử lý bất đồng bộ. Vui lòng thử lại."));
                    return null;
                });
    }

    private boolean isReplayAndRecord(String userId, int code) {
        long now = System.currentTimeMillis();
        ConcurrentHashMap<Integer, Long> userCodes = usedTotpCodes.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());

        // 90-second window accounts for maximum network latencies and standard clock skew tolerances
        userCodes.entrySet().removeIf(entry -> now - entry.getValue() > TOTP_REPLAY_WINDOW_MS);
        boolean isReplay = userCodes.putIfAbsent(code, now) != null;
        
        // Prevent Memory Leak: Evict empty maps from the outer structure
        if (userCodes.isEmpty()) {
            usedTotpCodes.remove(userId);
        }
        return isReplay;
    }

    @SuppressWarnings("unchecked")
    private void handleCreateDeposit(Object data, ClientHandler client, User currentUser) throws Exception {
        long amountVND;
        String totpCode = null;

        try {
            if (data instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) data;
                amountVND = parseAmountVND(map.get("amount"));
                Object codeObj = map.get("totpCode");
                if (codeObj != null && !codeObj.toString().isBlank()) {
                    totpCode = codeObj.toString().trim();
                }
            } else {
                amountVND = parseAmountVND(data);
            }
        } catch (AuctionExceptions.InvalidPayloadException e) {
            throw e;
        } catch (Exception e) {
            throw new AuctionExceptions.InvalidPayloadException("Định dạng tiền tệ không hợp lệ.");
        }

        if (currentUser.isTotpPaymentEnabled()) {
            if (totpCode == null) {
                client.sendResponse("REQUIRE_TOTP_PAYMENT", Map.of(
                        "message", "Giao dịch này yêu cầu xác thực TOTP. Vui lòng nhập mã 6 số.",
                        "amount", amountVND
                ));
                log.info("TOTP challenge issued for deposit {} by user {}", amountVND, currentUser.getUserName());
                return;
            }

            String secret = currentUser.getTotpSecret();
            if (secret == null) {
                client.sendResponse("ERROR", new ErrorPayload("ERR_PAY_003", "Lỗi cấu hình 2FA. Vui lòng tắt và bật lại TOTP trong Settings."));
                return;
            }

            int code;
            try {
                code = Integer.parseInt(totpCode);
            } catch (NumberFormatException e) {
                client.sendResponse("ERROR", new ErrorPayload("ERR_PAY_004", "Mã TOTP không đúng định dạng (phải là 6 chữ số)."));
                return;
            }

            if (!totpService.verifyCode(secret, code)) {
                client.sendResponse("INVALID_TOTP", Map.of("message", "Mã TOTP không hợp lệ hoặc đã hết hạn. Vui lòng thử lại."));
                log.warn("Invalid TOTP for payment by user {}", currentUser.getUserName());
                return;
            }
            if (isReplayAndRecord(currentUser.getId(), code)) {
                client.sendResponse("INVALID_TOTP", Map.of("message", "Mã TOTP này đã được sử dụng. Vui lòng đợi mã mới xuất hiện trên ứng dụng Authenticator."));
                log.warn("[SECURITY] TOTP replay attempt detected for user {} (code={})", currentUser.getUserName(), code);
                return;
            }
            log.info("TOTP verified for payment by user {}", currentUser.getUserName());
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
        if (data == null || data.toString().trim().isBlank()) {
            throw new AuctionExceptions.InvalidPayloadException("Mã đơn hàng nạp tiền không hợp lệ (trống).");
        }
        String orderId = data.toString().trim();
        DepositInfo depositInfo = pendingDeposits.get(orderId);

        if (depositInfo == null) {
            throw new AuctionExceptions.InvalidPayloadException("Đơn hàng không tồn tại hoặc đã hết hạn (15 phút).");
        }

        if (!depositInfo.getUser().getId().equals(currentUser.getId())) {
            log.warn("Security Alert: IDOR attempt! User {} tried to claim deposit owned by User {}. Order ID: {}", currentUser.getUserName(), depositInfo.getUser().getUserName(), orderId);
            throw new AuctionExceptions.UnauthorizedAccessException("Bạn không phải là chủ nhân của giao dịch này.");
        }

        if (!depositInfo.getIsProcessing().compareAndSet(false, true)) {
            throw new AuctionExceptions.InvalidPayloadException("Giao dịch đang được xử lý, vui lòng không gửi lại yêu cầu.");
        }

        try {
            boolean isCaptured = payPalService.captureOrder(orderId);
            if (isCaptured) {
                long verifiedAmount = payPalService.getCapturedAmountVND(orderId);
                paymentController.processDepositSuccess(depositInfo.getUser(), orderId, verifiedAmount)
                        .thenAcceptAsync(dbSuccess -> {
                            if (dbSuccess) {
                                pendingDeposits.remove(orderId);
                                client.sendResponse("DEPOSIT_SUCCESS", "Thanh toán thành công. Số dư đã được cập nhật.");
                            } else {
                                depositInfo.getIsProcessing().set(false);
                                client.sendResponse("ERROR", new ErrorPayload("ERR_DB_005", "Xác thực thành công nhưng lỗi lưu database. Xin liên hệ Admin."));
                            }
                        }, networkExecutor).exceptionally(ex -> {
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
            throw e;
        }
    }

    private void handleFetchWallet(ClientHandler client, User currentUser) throws Exception {
        Map<String, Object> walletData = walletDAO.getWalletData(currentUser.getId());
        client.sendResponse("FETCH_WALLET_SUCCESS", walletData);
    }

    private void startCleanupTask() {
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
                                paymentController.processDepositSuccess(info.getUser(), orderId, verifiedAmount)
                                        .thenAcceptAsync(dbSuccess -> {
                                            if (dbSuccess) {
                                                pendingDeposits.remove(orderId);
                                                info.getClient().sendResponse("DEPOSIT_SUCCESS", "Automatic payment successful. Balance updated.");
                                            } else {
                                                info.getIsProcessing().set(false);
                                            }
                                        }, networkExecutor);
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

    public static class DepositInfo {
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