package server.handler;

import controller.ServerPaymentController;
import model.user.User;
import network.NetworkMessage;
import server.ClientHandler;
import service.PayPalService;

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

    /** Service for interacting with the PayPal REST API. */
    private final PayPalService payPalService;

    /** Controller for persisting financial changes and wallet updates in the database. */
    private final ServerPaymentController paymentController;

    /**
     * A thread-safe map to store pending deposit data in RAM.
     * Maps PayPal Order IDs to their respective amount and creation timestamp.
     */
    private final Map<String, DepositInfo> pendingDeposits = new ConcurrentHashMap<>();

    /** Scheduler to periodically remove expired transactions from memory. */
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

    /** Maximum time (15 minutes) a pending deposit is allowed to stay in memory before expiration. */
    private static final long EXPIRATION_TIME_MS = 15 * 60 * 1000;

    /**
     * Constructs a new PaymentHandler and initializes the background cleanup task.
     */
    public PaymentHandler() {
        this.payPalService = new PayPalService();
        this.paymentController = new ServerPaymentController();
        startCleanupTask();
    }

    /**
     * Launches a background process that executes every 5 minutes to identify
     * and remove abandoned order records that have exceeded their TTL.
     */
    private void startCleanupTask() {
        cleanupScheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            int removedCount = 0;

            for (Map.Entry<String, DepositInfo> entry : pendingDeposits.entrySet()) {
                if (now - entry.getValue().getCreatedAt() > EXPIRATION_TIME_MS) {
                    pendingDeposits.remove(entry.getKey());
                    removedCount++;
                }
            }

            if (removedCount > 0) {
                System.out.println("[Payment]: Deleted " + YELLOW + removedCount + RESET + " suspending transactions");
            }
        }, 5, 5, TimeUnit.MINUTES);
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
                default:
                    System.out.println("[System](PaymentHandler.java): Invalid payment command: " + RED + command + RESET);
                    client.sendResponse("ERROR", "Invalid payment command: " + RED + command + RESET);
                    break;
            }
        } catch (Exception e) {
            System.out.println("[System](PaymentHandler.java): Error: " + RED + e.getMessage() + RESET);
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
        double amountVND;
        try {
            amountVND = Double.parseDouble(data.toString());
        } catch (NumberFormatException e) {
            client.sendResponse("ERROR", "Invalid currency format");
            return;
        }

        if (amountVND <= 0) {
            client.sendResponse("ERROR", "Deposit amount must be positive");
            return;
        }

        System.out.println("[System]: Creating deposit order of " + YELLOW + amountVND + RESET + " VND for \""
                + YELLOW + currentUser.getName() + RESET + "\"");

        String[] orderInfo = payPalService.createOrder(amountVND);
        String orderId = orderInfo[0];
        String approvalUrl = orderInfo[1];

        // Store the transaction state in RAM with a timestamp to manage TTL
        pendingDeposits.put(orderId, new DepositInfo(amountVND, System.currentTimeMillis()));

        Map<String, String> responseData = new HashMap<>();
        responseData.put("orderId", orderId);
        responseData.put("url", approvalUrl);

        client.sendResponse("PAYMENT_REDIRECT", responseData);
    }

    /**
     * Confirms and captures a completed PayPal transaction.
     * If the capture is successful, the funds are credited to the user's wallet
     * through an atomic database transaction.
     *
     * @param data        The PayPal Order ID to be verified.
     * @param client      The client handler for sending success or error feedback.
     * @param currentUser The user confirming the deposit.
     * @throws Exception If an error occurs during payment capture or balance update.
     */
    private void handleConfirmDeposit(Object data, ClientHandler client, User currentUser) throws Exception {
        String orderId = data.toString().trim();

        DepositInfo depositInfo = pendingDeposits.get(orderId);

        if (depositInfo == null) {
            client.sendResponse("ERROR", "Order does not exist, is expired, or is processed");
            return;
        }

        // Attempt to capture the authorized payment from PayPal
        boolean isCaptured = payPalService.captureOrder(orderId);

        if (isCaptured) {
            double amountVND = depositInfo.getAmountVND();

            // Credit the user's wallet and log the transaction atomically
            boolean dbSuccess = paymentController.processDepositSuccess(currentUser, amountVND, orderId);

            if (dbSuccess) {
                client.sendResponse("DEPOSIT_SUCCESS", "Successful transaction. Deposited " + amountVND + " VND to balance.");
                pendingDeposits.remove(orderId); // Remove from memory immediately upon completion
            } else {
                client.sendResponse("ERROR", "Money is deducted but not deposited. Please contact Admins.");
            }
        } else {
            client.sendResponse("ERROR", "Transaction is not completed or is canceled.");
        }
    }

    /**
     * A lightweight state storage class to track a pending deposit's metadata.
     * Stores the monetary amount and the time of creation to facilitate garbage collection.
     */
    private static class DepositInfo {
        private final double amountVND;
        private final long createdAt;

        /**
         * Constructs a new state object for a pending deposit.
         *
         * @param amountVND The amount in Vietnamese Dong.
         * @param createdAt The system time in milliseconds when the order was initiated.
         */
        public DepositInfo(double amountVND, long createdAt) {
            this.amountVND = amountVND;
            this.createdAt = createdAt;
        }

        public double getAmountVND() { return amountVND; }
        public long getCreatedAt() { return createdAt; }
    }
}