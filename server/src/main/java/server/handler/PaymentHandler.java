package server.handler;

import database.dao.WalletDAO;
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

/**
 * Handles payment-related commands, specifically facilitating deposits via PayPal.
 * This handler manages the lifecycle of a deposit transaction, from creating an
 * initial order to capturing the final payment and updating the user's wallet balance.
 * It utilizes a background cleanup task to prevent memory leaks from abandoned transactions.
 */
public class PaymentHandler implements CommandHandler {
    private static final Logger log = LoggerFactory.getLogger(PaymentHandler.class);
    private final WalletDAO walletDAO = new WalletDAO();

    /**
     * Service for interacting with the PayPal REST API.
     */
    private final PayPalService payPalService;

    /**
     * Controller for persisting financial changes and wallet updates in the database.
     */
    private final ServerPaymentController paymentController;

    /**
     * A thread-safe map to store pending deposit data in RAM.
     * Maps PayPal Order IDs to their respective amount and creation timestamp.
     */
    private final Map<String, DepositInfo> pendingDeposits = new ConcurrentHashMap<>();

    /**
     * Scheduler to periodically remove expired transactions from memory.
     */
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * Maximum time (15 minutes) a pending deposit is allowed to stay in memory before expiration.
     */
    private static final long EXPIRATION_TIME_MS = 15 * 60 * 1000;

    /**
     * Constructs a new PaymentHandler and initializes the background cleanup task.
     *
     * @param paymentController The controller for financial operations.
     */
    public PaymentHandler(ServerPaymentController paymentController) {
        this.payPalService = new PayPalService();
        this.paymentController = paymentController;
        startCleanupTask();
    }

    /**
     * Launches a background process that executes every 10 seconds to identify
     * and remove abandoned order records that have exceeded their TTL,
     * or auto-capture APPROVED orders.
     */
    private void startCleanupTask() {
        cleanupScheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();

            for (Map.Entry<String, DepositInfo> entry : pendingDeposits.entrySet()) {
                String orderId = entry.getKey();
                DepositInfo info = entry.getValue();

                // Clean if the order has waited for at least 15 minutes
                if (now - info.getCreatedAt() > EXPIRATION_TIME_MS) {
                    pendingDeposits.remove(orderId);
                    log.info("Removed expired pending deposit: {}", orderId);
                    continue;
                }

                // Lấy cờ xử lý để tránh tranh chấp với luồng xác nhận thủ công của Client
                if (info.getIsProcessing().compareAndSet(false, true)) {
                    try {
                        String status = payPalService.getOrderStatus(orderId);

                        if ("APPROVED".equals(status)) {
                            log.info("Auto-detected APPROVED status for Order: {}. Attempting capture...", orderId);
                            boolean isCaptured = payPalService.captureOrder(orderId);

                            if (isCaptured) {
                                long verifiedAmount = payPalService.getCapturedAmountVND(orderId);

                                if (verifiedAmount != info.getAmountVND()) {
                                    log.warn("Price manipulation warning during auto-cleanup: PayPal amount ({}) != requested amount ({}) for Order ID: {}",
                                            verifiedAmount, info.getAmountVND(), orderId);
                                }

                                paymentController.processDepositSuccess(info.getUser(), orderId, verifiedAmount).thenAccept(dbSuccess -> {
                                    if (dbSuccess) {
                                        // Chỉ remove khi mọi thứ đã thành công trót lọt
                                        pendingDeposits.remove(orderId);
                                        info.getClient().sendResponse("DEPOSIT_SUCCESS", "Automatic payment successful. Balance updated.");
                                    } else {
                                        info.getIsProcessing().set(false); // Nhả lock nếu DB lỗi để thử lại sau
                                    }
                                });
                            } else {
                                info.getIsProcessing().set(false); // Capture xịt, nhả lock
                            }
                        } else {
                            info.getIsProcessing().set(false); // Status chưa APPROVED, nhả lock chờ lượt sau
                        }
                    } catch (Exception e) {
                        log.warn("Error polling PayPal order {}: {}", orderId, e.getMessage());
                        info.getIsProcessing().set(false); // Có lỗi mạng, nhả lock an toàn
                    }
                }
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    /**
     * Routes payment commands to their specific logic handlers.
     */
    @Override
    public void handle(NetworkMessage message, ClientHandler client) {
        String command = message.getCommand();
        Object data = message.getData();

        User currentUser = client.getUser();

        if (currentUser == null) {
            client.sendResponse("ERROR", "You are not logged in");
            return;
        }

        try {
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
                    log.warn("Invalid payment command: {}", command);
                    client.sendResponse("ERROR", "Invalid payment command: " + RED + command + RESET);
                    break;
            }
        } catch (Exception e) {
            log.error("Payment Handle Error: {}", e.getMessage());
            client.sendResponse("ERROR", "Server error: " + e.getMessage());
        }
    }

    /**
     * Initiates a new deposit request by creating a PayPal order.
     */
    private void handleCreateDeposit(Object data, ClientHandler client, User currentUser) throws Exception {
        long amountVND;
        try {
            amountVND = Long.parseLong(data.toString());
        } catch (NumberFormatException e) {
            client.sendResponse("ERROR", "Invalid currency format");
            return;
        }

        if (amountVND <= 0) {
            client.sendResponse("ERROR", "Deposit amount must be positive");
            return;
        }

        log.info("Creating deposit order of {} VND for {}", amountVND, currentUser.getUserName());
        String[] orderInfo = payPalService.createOrder(amountVND);
        String orderId = orderInfo[0];
        String approvalUrl = orderInfo[1];

        // Store the transaction state in RAM with client and user info
        pendingDeposits.put(orderId, new DepositInfo(amountVND, System.currentTimeMillis(), client, currentUser));

        Map<String, String> responseData = new HashMap<>();
        responseData.put("orderId", orderId);
        responseData.put("url", approvalUrl);

        client.sendResponse("PAYMENT_REDIRECT", responseData);
    }

    /**
     * Confirms and captures a completed PayPal transaction.
     */
    private void handleConfirmDeposit(Object data, ClientHandler client, User currentUser) {
        String orderId = data.toString().trim();

        // Lấy thông tin mà không rút khỏi Map
        DepositInfo depositInfo = pendingDeposits.get(orderId);

        if (depositInfo == null) {
            client.sendResponse("ERROR", "Order does not exist or is expired.");
            return;
        }

        // [SECURITY FIX]: IDOR Prevention
        if (!depositInfo.getUser().getId().equals(currentUser.getId())) {
            log.warn("Security Alert: IDOR attempt! User {} tried to claim deposit owned by User {}. Order ID: {}",
                    currentUser.getUserName(), depositInfo.getUser().getUserName(), orderId);
            client.sendResponse("ERROR", "Unauthorized: You are not the owner of this transaction.");
            return;
        }

        // [ARCHITECT FIX]: Sử dụng Atomic Lock thay cho việc Remove khỏi Map
        if (!depositInfo.getIsProcessing().compareAndSet(false, true)) {
            client.sendResponse("ERROR", "Transaction is currently being processed. Please wait.");
            return;
        }

        try {
            // Attempt to capture the authorized payment from PayPal
            boolean isCaptured = payPalService.captureOrder(orderId);

            if (isCaptured) {
                long verifiedAmount = payPalService.getCapturedAmountVND(orderId);

                if (verifiedAmount != depositInfo.getAmountVND()) {
                    log.warn("Price manipulation warning: PayPal returned amount ({}) differs from client request ({}) for Order ID: {}",
                            verifiedAmount, depositInfo.getAmountVND(), orderId);
                }

                paymentController.processDepositSuccess(depositInfo.getUser(), orderId, verifiedAmount).thenAccept(dbSuccess -> {
                    if (dbSuccess) {
                        // DB xong xuôi mới an tâm dọn rác trong RAM
                        pendingDeposits.remove(orderId);
                        client.sendResponse("DEPOSIT_SUCCESS", "Successful transaction. Your balance will be updated shortly.");
                    } else {
                        depositInfo.getIsProcessing().set(false); // Nhả lock nếu DB lỗi
                        client.sendResponse("ERROR", "Payment verification failed or database error. Please contact Admins.");
                    }
                }).exceptionally(ex -> {
                    depositInfo.getIsProcessing().set(false);
                    client.sendResponse("ERROR", "Database logging error.");
                    return null;
                });

            } else {
                depositInfo.getIsProcessing().set(false); // Capture lỗi, nhả lock
                client.sendResponse("ERROR", "Transaction is not completed or is canceled.");
            }
        } catch (Exception e) {
            depositInfo.getIsProcessing().set(false); // Bắt ngoại lệ an toàn, nhả lock
            log.error("Error confirming deposit {}: {}", orderId, e.getMessage());
            client.sendResponse("ERROR", "Connection error with payment gateway.");
        }
    }

    /**
     * A lightweight state storage class to track a pending deposit's metadata.
     */
    private static class DepositInfo {
        private final long amountVND;
        private final long createdAt;
        private final ClientHandler client;
        private final User user;

        // Cờ đánh dấu luồng xử lý độc quyền (Thread-safe Lock)
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

    private void handleFetchWallet(ClientHandler client, User currentUser) {
        try {
            Map<String, Object> walletData = walletDAO.getWalletData(currentUser.getId());
            client.sendResponse("FETCH_WALLET_SUCCESS", walletData);
        } catch (SQLException e) {
            log.error("Error fetching wallet for {}: {}", currentUser.getUserName(), e.getMessage());
            client.sendResponse("ERROR", "Cannot retrieve wallet data.");
        }
    }
}