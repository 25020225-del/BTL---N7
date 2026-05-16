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
     * Launches a background process that executes every 5 minutes to identify
     * and remove abandoned order records that have exceeded their TTL.
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

                // Automatically check order status from PayPal
                try {
                    String status = payPalService.getOrderStatus(orderId);

                    if ("APPROVED".equals(status)) {
                        DepositInfo processingInfo = pendingDeposits.remove(orderId);

                        if (processingInfo != null) {
                            log.info("Auto-detected APPROVED status for Order: {}. Attempting capture...", orderId);
                            boolean isCaptured = payPalService.captureOrder(orderId);

                            if (isCaptured) {
                                // Fetch the actual verified amount from PayPal API
                                long verifiedAmount = payPalService.getCapturedAmountVND(orderId);

                                if (verifiedAmount != processingInfo.getAmountVND()) {
                                    log.warn("Price manipulation warning during auto-cleanup: PayPal amount ({}) != requested amount ({}) for Order ID: {}",
                                            verifiedAmount, processingInfo.getAmountVND(), orderId);
                                }

                                paymentController.processDepositSuccess(processingInfo.getUser(), orderId, verifiedAmount).thenAccept(dbSuccess -> {
                                    if (dbSuccess) {
                                        processingInfo.getClient().sendResponse("DEPOSIT_SUCCESS", "Automatic payment successful. Balance updated.");
                                    }
                                });
                            } else {
                                // Trả lại vào RAM nếu capture thất bại
                                pendingDeposits.put(orderId, processingInfo);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error polling PayPal order {}: {}", orderId, e.getMessage());
                }
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    /**
     * Routes payment commands to their specific logic handlers.
     * Requires the user to be authenticated before processing any financial request.
     *
     * @param message The network message containing the payment command (CREATE_DEPOSIT, CONFIRM_DEPOSIT).
     * @param client  The handler for the active client connection.
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
            log.error("{}", e.getMessage());
            client.sendResponse("ERROR", "Server error: " + e.getMessage());
        }
    }

    /**
     * Initiates a new deposit request by creating a PayPal order.
     * The order details are stored in memory for later verification.
     *
     * @param data        The deposit amount provided by the client.
     * @param client      The client handler for sending the redirect response.
     * @param currentUser The user making the deposit.
     * @throws Exception If an error occurs during order creation with PayPal.
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
     * If successful, funds are credited asynchronously to avoid thread starvation.
     *
     * @param data        The PayPal Order ID to be verified.
     * @param client      The client handler for sending success or error feedback.
     * @param currentUser The user confirming the deposit.
     * @throws Exception If an error occurs during payment capture or balance update.
     */
    private void handleConfirmDeposit(Object data, ClientHandler client, User currentUser) throws Exception {
        String orderId = data.toString().trim();

        // [SECURITY FIX]: Lấy nguyên tử. Luồng nào giật được data ra trước sẽ độc quyền xử lý.
        DepositInfo depositInfo = pendingDeposits.remove(orderId);

        if (depositInfo == null) {
            client.sendResponse("ERROR", "Order does not exist, is already being processed, or is expired.");
            return;
        }

        // Attempt to capture the authorized payment from PayPal
        boolean isCaptured = payPalService.captureOrder(orderId);
        if (isCaptured) {
            // Fetch the actual verified amount from PayPal API
            long verifiedAmount = payPalService.getCapturedAmountVND(orderId);

            if (verifiedAmount != depositInfo.getAmountVND()) {
                log.warn("Price manipulation warning: PayPal returned amount ({}) differs from client request ({}) for Order ID: {}",
                        verifiedAmount, depositInfo.getAmountVND(), orderId);
            }

            paymentController.processDepositSuccess(currentUser, orderId, verifiedAmount).thenAccept(dbSuccess -> {
                if (dbSuccess) {
                    client.sendResponse("DEPOSIT_SUCCESS", "Successful transaction. Your balance will be updated shortly.");
                } else {
                    client.sendResponse("ERROR", "Payment verification failed or database error. Please contact Admins.");
                }
            }).exceptionally(ex -> {
                client.sendResponse("ERROR", "Database logging error.");
                return null;
            });
        } else {
            // return info into the queue if transaction is failed
            pendingDeposits.put(orderId, depositInfo);
            client.sendResponse("ERROR", "Transaction is not completed or is canceled.");
        }
    }

    /**
     * A lightweight state storage class to track a pending deposit's metadata.
     * Stores the monetary amount and the time of creation to facilitate garbage collection.
     */
    private static class DepositInfo {
        private final long amountVND;
        private final long createdAt;
        private final ClientHandler client;
        private final User user;

        /**
         * Constructs a new state object for a pending deposit.
         *
         * @param amountVND The amount in Vietnamese Dong.
         * @param createdAt The system time in milliseconds when the order was initiated.
         */
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