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
 * Xử lý tất cả các lệnh liên quan đến thanh toán và giao dịch tài chính.
 *
 * <p><b>Danh sách lệnh được xử lý:</b></p>
 * <pre>
 *   CREATE_DEPOSIT   → Tạo lệnh nạp tiền qua PayPal.
 *   CONFIRM_DEPOSIT  → Xác nhận lệnh nạp tiền sau khi user thanh toán PayPal.
 *   FETCH_WALLET     → Lấy số dư và lịch sử giao dịch.
 *   REQUEST_WITHDRAW → [NEW] Tạo yêu cầu rút tiền (Maker step).
 * </pre>
 *
 * <p><b>Bảo mật TOTP:</b> Cả nạp tiền và rút tiền đều hỗ trợ cơ chế
 * Challenge-Response nếu user đã bật {@code totp_payment_enabled}.</p>
 */
public class PaymentHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentHandler.class);

    private final PayPalService payPalService;
    private final ServerPaymentController paymentController;
    private final TOTPService totpService;
    private final WalletDAO walletDAO;

    /** Các lệnh nạp tiền đang chờ xử lý (orderId → DepositInfo). */
    public static final Map<String, DepositInfo> pendingDeposits = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cleanupScheduler =
            Executors.newSingleThreadScheduledExecutor();
    private static final long DEPOSIT_EXPIRATION_MS = 15 * 60 * 1000L; // 15 phút

    public PaymentHandler(ServerPaymentController paymentController,
                          TOTPService totpService,
                          WalletDAO walletDAO) {
        this.payPalService      = new PayPalService();
        this.paymentController  = paymentController;
        this.totpService        = totpService;
        this.walletDAO          = walletDAO;
        startCleanupTask();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DISPATCHER
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void handle(NetworkMessage message, ClientHandler client) throws Exception {
        String command     = message.getCommand();
        Object data        = message.getData();
        User currentUser   = client.getUser();

        // Guard: Tất cả lệnh tài chính đều yêu cầu xác thực
        if (currentUser == null) {
            throw new AuctionExceptions.UnauthorizedAccessException(
                    "Bạn cần đăng nhập để thực hiện giao dịch này.");
        }

        switch (command) {
            case "CREATE_DEPOSIT"   -> handleCreateDeposit(data, client, currentUser);
            case "CONFIRM_DEPOSIT"  -> handleConfirmDeposit(data, client, currentUser);
            case "FETCH_WALLET"     -> handleFetchWallet(client, currentUser);
            case "REQUEST_WITHDRAW" -> handleRequestWithdraw(data, client, currentUser);
            default -> throw new AuctionExceptions.InvalidPayloadException(
                    "Lệnh thanh toán không hợp lệ: " + command);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQUEST_WITHDRAW — [NEW]
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xử lý yêu cầu rút tiền từ User (Maker step trong mô hình Maker-Checker).
     *
     * <p><b>Giao thức payload từ Client:</b></p>
     * <pre>
     *   Lần 1 (không có TOTP):
     *     {
     *       "amount":        500000,
     *       "payoutMethod":  "BANK_TRANSFER",
     *       "payoutDetails": "Ngân hàng: Vietcombank | STK: 0123456789 | Chủ TK: Nguyen Van A"
     *     }
     *
     *   Lần 2 (có TOTP — khi server phản hồi REQUIRE_TOTP_PAYMENT):
     *     {
     *       "amount":        500000,
     *       "payoutMethod":  "BANK_TRANSFER",
     *       "payoutDetails": "Ngân hàng: Vietcombank | STK: 0123456789 | Chủ TK: Nguyen Van A",
     *       "totpCode":      "123456"
     *     }
     * </pre>
     *
     * <p><b>Logic server:</b></p>
     * <pre>
     *   isTotpPaymentEnabled = false → bỏ qua TOTP, xử lý luôn
     *   isTotpPaymentEnabled = true, totpCode rỗng → REQUIRE_TOTP_PAYMENT
     *   isTotpPaymentEnabled = true, totpCode sai  → INVALID_TOTP
     *   isTotpPaymentEnabled = true, totpCode đúng → xử lý tạo yêu cầu
     * </pre>
     *
     * @param data        Payload từ client (Map hoặc JSON được deserialize).
     * @param client      ClientHandler của phiên kết nối hiện tại.
     * @param currentUser User đã được xác thực.
     */
    @SuppressWarnings("unchecked")
    private void handleRequestWithdraw(Object data, ClientHandler client, User currentUser)
            throws Exception {

        // ── BƯỚC 1: Parse payload ────────────────────────────────────────────
        long amountVND;
        String payoutMethod;
        String payoutDetails;
        String totpCode = null;

        try {
            Map<String, Object> map = (Map<String, Object>) data;

            amountVND     = Long.parseLong(map.get("amount").toString());
            payoutMethod  = (String) map.get("payoutMethod");
            payoutDetails = (String) map.get("payoutDetails");

            Object codeObj = map.get("totpCode");
            if (codeObj != null && !codeObj.toString().isBlank()) {
                totpCode = codeObj.toString().trim();
            }
        } catch (ClassCastException | NullPointerException | NumberFormatException e) {
            throw new AuctionExceptions.InvalidPayloadException(
                    "Dữ liệu yêu cầu rút tiền không đúng định dạng. "
                            + "Vui lòng kiểm tra lại amount, payoutMethod và payoutDetails.");
        }

        // ── BƯỚC 2: Validate nghiệp vụ cơ bản ──────────────────────────────
        if (amountVND <= 0) {
            throw new AuctionExceptions.InvalidPayloadException(
                    "Số tiền rút phải lớn hơn 0.");
        }
        if (payoutMethod == null || payoutMethod.isBlank()) {
            throw new AuctionExceptions.InvalidPayloadException(
                    "Phương thức nhận tiền không được để trống.");
        }
        if (payoutDetails == null || payoutDetails.isBlank()) {
            throw new AuctionExceptions.InvalidPayloadException(
                    "Thông tin tài khoản nhận không được để trống.");
        }

        // ── BƯỚC 3: TOTP Challenge-Response (giống luồng nạp tiền) ──────────
        if (currentUser.isTotpPaymentEnabled()) {
            if (totpCode == null) {
                // Thách thức Client — chưa có mã OTP
                client.sendResponse("REQUIRE_TOTP_PAYMENT",
                        Map.of(
                                "message",       "Giao dịch rút tiền này yêu cầu xác thực TOTP. "
                                        + "Vui lòng nhập mã 6 số từ ứng dụng Authenticator.",
                                "amount",        amountVND,
                                "payoutMethod",  payoutMethod,
                                "payoutDetails", payoutDetails
                        ));
                log.info("[WITHDRAW] TOTP challenge issued for user {} (amount={})",
                        currentUser.getUserName(), amountVND);
                return;
            }

            // Validate mã TOTP
            String secret = currentUser.getTotpSecret();
            if (secret == null) {
                client.sendResponse("ERROR", new ErrorPayload("ERR_PAY_003",
                        "Lỗi cấu hình 2FA. Vui lòng tắt và bật lại TOTP trong Settings."));
                return;
            }

            int code;
            try {
                code = Integer.parseInt(totpCode);
            } catch (NumberFormatException e) {
                client.sendResponse("ERROR", new ErrorPayload("ERR_PAY_004",
                        "Mã TOTP không đúng định dạng (phải là 6 chữ số)."));
                return;
            }

            if (!totpService.verifyCode(secret, code)) {
                client.sendResponse("INVALID_TOTP",
                        Map.of("message", "Mã TOTP không hợp lệ hoặc đã hết hạn. Vui lòng thử lại."));
                log.warn("[WITHDRAW] Invalid TOTP for user {} (amount={})",
                        currentUser.getUserName(), amountVND);
                return;
            }
            if (isReplayAndRecord(currentUser.getId(), code)) {
                client.sendResponse("INVALID_TOTP",
                        Map.of("message",
                                "Mã TOTP này đã được sử dụng. "
                                        + "Vui lòng đợi mã mới xuất hiện trên ứng dụng Authenticator."));
                log.warn("[SECURITY] TOTP replay attempt detected for user {} (code={})",
                        currentUser.getUserName(), code);
                return;
            }

            log.info("[WITHDRAW] TOTP verified for user {} (amount={})",
                    currentUser.getUserName(), amountVND);
        }

        // ── BƯỚC 4: Đưa task vào TransactionManager để xử lý bất đồng bộ ───
        final long finalAmount        = amountVND;
        final String finalMethod      = payoutMethod;
        final String finalDetails     = payoutDetails;

        paymentController.createWithdrawalRequest(
                currentUser, finalAmount, finalMethod, finalDetails
        ).thenAccept(result -> {
            switch (result) {
                case "SUCCESS" -> {
                    client.sendResponse("WITHDRAW_REQUEST_SUCCESS",
                            Map.of(
                                    "message", "Yêu cầu rút " + finalAmount
                                            + " VND đã được ghi nhận và đang chờ Admin duyệt.",
                                    "amount",  finalAmount
                            ));
                    log.info("[WITHDRAW] Request created successfully for user {} (amount={})",
                            currentUser.getUserName(), finalAmount);
                }
                case "INSUFFICIENT_FUNDS" -> {
                    client.sendResponse("ERROR", new ErrorPayload("ERR_PAY_010",
                            "Số dư khả dụng không đủ để thực hiện yêu cầu rút tiền này."));
                }
                case "INVALID_AMOUNT" -> {
                    client.sendResponse("ERROR", new ErrorPayload("ERR_PAY_011",
                            "Số tiền rút không hợp lệ (phải lớn hơn 0)."));
                }
                default -> {
                    // DB_ERROR hoặc các lỗi không lường trước
                    client.sendResponse("ERROR", new ErrorPayload("ERR_DB_005",
                            "Lỗi hệ thống khi tạo yêu cầu rút tiền. Vui lòng thử lại hoặc liên hệ Admin."));
                }
            }
        }).exceptionally(ex -> {
            log.error("[WITHDRAW] Unexpected error in async chain for user {}: {}",
                    currentUser.getUserName(), ex.getMessage(), ex);
            client.sendResponse("ERROR", new ErrorPayload("ERR_SYS_500",
                    "Lỗi đứt gãy luồng xử lý bất đồng bộ. Vui lòng thử lại."));
            return null;
        });
    }

    /**
     * Cache tracking recently used TOTP codes per user.
     *
     * <p>Key: {@code userId}. Value: map of {@code (totpCode → expiryTimeMillis)}.
     * An entry is considered valid if it was inserted within the last 90 seconds
     * (3× the TOTP 30-second window), covering any clock skew the server already
     * permits via the TOTPService window tolerance.</p>
     *
     * <p>This prevents a valid code from being replayed within its active window
     * to create multiple withdrawal/deposit requests.</p>
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, Long>> usedTotpCodes =
            new ConcurrentHashMap<>();

    private static final long TOTP_REPLAY_WINDOW_MS = 90_000L; // 90 seconds

    /**
     * Checks whether the given TOTP code has already been used by this user
     * within the replay prevention window, and records it if not.
     *
     * @param userId The authenticated user's ID.
     * @param code   The integer TOTP code that was verified as correct.
     * @return {@code true} if the code is a replay (already used); {@code false} if fresh.
     */
    private boolean isReplayAndRecord(String userId, int code) {
        long now = System.currentTimeMillis();

        ConcurrentHashMap<Integer, Long> userCodes =
                usedTotpCodes.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());

        // Evict expired entries for this user (keep map small).
        userCodes.entrySet().removeIf(entry -> now - entry.getValue() > TOTP_REPLAY_WINDOW_MS);

        // putIfAbsent returns null if the key was absent (first use → not a replay).
        Long previous = userCodes.putIfAbsent(code, now);
        return previous != null; // non-null means the code was already present
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CÁC HANDLER HIỆN CÓ (giữ nguyên)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xử lý nạp tiền với cơ chế Challenge-Response TOTP (Stateless).
     *
     * <p><b>Giao thức payload từ Client:</b></p>
     * <pre>
     *   Lần 1 (không có TOTP): Map { "amount": 100000 }
     *   Lần 2 (có TOTP):       Map { "amount": 100000, "totpCode": "123456" }
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private void handleCreateDeposit(Object data, ClientHandler client, User currentUser) throws Exception {
        long amountVND;
        String totpCode = null;

        try {
            if (data instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) data;
                amountVND = Long.parseLong(map.get("amount").toString());
                Object codeObj = map.get("totpCode");
                if (codeObj != null && !codeObj.toString().isBlank()) {
                    totpCode = codeObj.toString().trim();
                }
            } else {
                amountVND = Long.parseLong(data.toString());
            }
        } catch (Exception e) {
            throw new AuctionExceptions.InvalidPayloadException("Định dạng tiền tệ không hợp lệ.");
        }

        if (amountVND <= 0) {
            throw new AuctionExceptions.InvalidPayloadException("Số tiền nạp phải lớn hơn 0.");
        }

        if (currentUser.isTotpPaymentEnabled()) {
            if (totpCode == null) {
                client.sendResponse("REQUIRE_TOTP_PAYMENT",
                        Map.of(
                                "message", "Giao dịch này yêu cầu xác thực TOTP. Vui lòng nhập mã 6 số.",
                                "amount",  amountVND
                        ));
                log.info("TOTP challenge issued for deposit {} by user {}",
                        amountVND, currentUser.getUserName());
                return;
            }

            String secret = currentUser.getTotpSecret();
            if (secret == null) {
                client.sendResponse("ERROR", new ErrorPayload("ERR_PAY_003",
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

            if (!totpService.verifyCode(secret, code)) {
                client.sendResponse("INVALID_TOTP",
                        Map.of("message", "Mã TOTP không hợp lệ hoặc đã hết hạn. Vui lòng thử lại."));
                log.warn("Invalid TOTP for payment by user {}", currentUser.getUserName());
                return;
            }
            if (isReplayAndRecord(currentUser.getId(), code)) {
                client.sendResponse("INVALID_TOTP",
                        Map.of("message",
                                "Mã TOTP này đã được sử dụng. "
                                        + "Vui lòng đợi mã mới xuất hiện trên ứng dụng Authenticator."));
                log.warn("[SECURITY] TOTP replay attempt detected for user {} (code={})",
                        currentUser.getUserName(), code);
                return;
            }
            log.info("TOTP verified for payment by user {}", currentUser.getUserName());
        }

        log.info("Creating deposit order of {} VND for {}", amountVND, currentUser.getUserName());
        String[] orderInfo   = payPalService.createOrder(amountVND);
        String orderId       = orderInfo[0];
        String approvalUrl   = orderInfo[1];

        pendingDeposits.put(orderId,
                new DepositInfo(amountVND, System.currentTimeMillis(), client, currentUser));

        Map<String, String> responseData = new HashMap<>();
        responseData.put("orderId", orderId);
        responseData.put("url",     approvalUrl);
        client.sendResponse("PAYMENT_REDIRECT", responseData);
    }

    private void handleConfirmDeposit(Object data, ClientHandler client, User currentUser) throws Exception {
        String orderId         = data.toString().trim();
        DepositInfo depositInfo = pendingDeposits.get(orderId);

        if (depositInfo == null) {
            throw new AuctionExceptions.InvalidPayloadException(
                    "Đơn hàng không tồn tại hoặc đã hết hạn (15 phút).");
        }

        if (!depositInfo.getUser().getId().equals(currentUser.getId())) {
            log.warn("Security Alert: IDOR attempt! User {} tried to claim deposit owned by User {}. Order ID: {}",
                    currentUser.getUserName(), depositInfo.getUser().getUserName(), orderId);
            throw new AuctionExceptions.UnauthorizedAccessException(
                    "Bạn không phải là chủ nhân của giao dịch này.");
        }

        if (!depositInfo.getIsProcessing().compareAndSet(false, true)) {
            throw new AuctionExceptions.InvalidPayloadException(
                    "Giao dịch đang được xử lý, vui lòng không gửi lại yêu cầu.");
        }

        try {
            boolean isCaptured = payPalService.captureOrder(orderId);
            if (isCaptured) {
                long verifiedAmount = payPalService.getCapturedAmountVND(orderId);
                paymentController.processDepositSuccess(depositInfo.getUser(), orderId, verifiedAmount)
                        .thenAccept(dbSuccess -> {
                            if (dbSuccess) {
                                pendingDeposits.remove(orderId);
                                client.sendResponse("DEPOSIT_SUCCESS",
                                        "Thanh toán thành công. Số dư đã được cập nhật.");
                            } else {
                                depositInfo.getIsProcessing().set(false);
                                client.sendResponse("ERROR", new ErrorPayload("ERR_DB_005",
                                        "Xác thực thành công nhưng lỗi lưu database. Xin liên hệ Admin."));
                            }
                        }).exceptionally(ex -> {
                            depositInfo.getIsProcessing().set(false);
                            client.sendResponse("ERROR", new ErrorPayload("ERR_SYS_500",
                                    "Lỗi đứt gãy luồng xử lý bất đồng bộ."));
                            return null;
                        });
            } else {
                depositInfo.getIsProcessing().set(false);
                throw new AuctionExceptions.InvalidPayloadException(
                        "Giao dịch chưa được hoàn tất hoặc đã bị hủy trên PayPal.");
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

    // ─────────────────────────────────────────────────────────────────────────
    // CLEANUP SCHEDULER
    // ─────────────────────────────────────────────────────────────────────────

    private void startCleanupTask() {
        cleanupScheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();

            for (Map.Entry<String, DepositInfo> entry : pendingDeposits.entrySet()) {
                String orderId   = entry.getKey();
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
                                        .thenAccept(dbSuccess -> {
                                            if (dbSuccess) {
                                                pendingDeposits.remove(orderId);
                                                info.getClient().sendResponse("DEPOSIT_SUCCESS",
                                                        "Automatic payment successful. Balance updated.");
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

    // ─────────────────────────────────────────────────────────────────────────
    // INNER CLASS — DepositInfo (giữ nguyên)
    // ─────────────────────────────────────────────────────────────────────────

    public static class DepositInfo {
        private final long amountVND;
        private final long createdAt;
        private final ClientHandler client;
        private final User user;
        private final AtomicBoolean isProcessing = new AtomicBoolean(false);

        public DepositInfo(long amountVND, long createdAt, ClientHandler client, User user) {
            this.amountVND = amountVND;
            this.createdAt = createdAt;
            this.client    = client;
            this.user      = user;
        }

        public long getAmountVND()          { return amountVND; }
        public long getCreatedAt()          { return createdAt; }
        public ClientHandler getClient()    { return client; }
        public User getUser()               { return user; }
        public AtomicBoolean getIsProcessing() { return isProcessing; }
    }
}