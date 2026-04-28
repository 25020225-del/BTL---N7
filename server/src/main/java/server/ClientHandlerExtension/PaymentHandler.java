package server.ClientHandlerExtension;

import controller.ServerPaymentController;
import model.User;
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

public class PaymentHandler implements CommandHandler {

    private final PayPalService payPalService;
    private final ServerPaymentController paymentController;

    // Use ConcurrentHashMap for safety in a multithreaded environment
    private final Map<String, DepositInfo> pendingDeposits = new ConcurrentHashMap<>();

    // Scheduler for cleaning up stuck transactions (Memory Leak Prevention)
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
    private static final long EXPIRATION_TIME_MS = 15 * 60 * 1000; // 15 minutes

    public PaymentHandler() {
        this.payPalService = new PayPalService();
        this.paymentController = new ServerPaymentController();
        startCleanupTask();
    }

    /**
     * Launch a background process to clean up abandoned order records.
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
        }, 5, 5, TimeUnit.MINUTES); // Run every 5 minutes
    }

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

        // Add to Map with a timestamp to manage TTL (Time-To-Live)
        pendingDeposits.put(orderId, new DepositInfo(amountVND, System.currentTimeMillis()));

        Map<String, String> responseData = new HashMap<>();
        responseData.put("orderId", orderId);
        responseData.put("url", approvalUrl);

        client.sendResponse("PAYMENT_REDIRECT", responseData);
    }

    private void handleConfirmDeposit(Object data, ClientHandler client, User currentUser) throws Exception {
        String orderId = data.toString().trim();

        DepositInfo depositInfo = pendingDeposits.get(orderId);

        if (depositInfo == null) {
            client.sendResponse("ERROR", "Order does not exist, is expired, or is processed");
            return;
        }

        boolean isCaptured = payPalService.captureOrder(orderId);

        if (isCaptured) {
            double amountVND = depositInfo.getAmountVND();

            boolean dbSuccess = paymentController.processDepositSuccess(currentUser, amountVND, orderId);

            if (dbSuccess) {
                client.sendResponse("DEPOSIT_SUCCESS", "Successful transaction. Deposited " + amountVND + " VND to balance.");
                pendingDeposits.remove(orderId); // Delete right after the order is completed
            } else {
                client.sendResponse("ERROR", "Money is deducted but not deposited. Please contact Admins.");
            }
        } else {
            client.sendResponse("ERROR", "Transaction is not completed or is canceled.");
        }
    }

    // --- STATE STORAGE SUPPORT CLASS ---
    /**
     * Wrapper for storing the amount along with the order creation time,
     * to handle garbage collection for expired transactions.
     */
    private static class DepositInfo {
        private final double amountVND;
        private final long createdAt;

        public DepositInfo(double amountVND, long createdAt) {
            this.amountVND = amountVND;
            this.createdAt = createdAt;
        }

        public double getAmountVND() { return amountVND; }
        public long getCreatedAt() { return createdAt; }
    }
}