package server.ClientHandlerExtension;

import controller.ServerPaymentController;
import model.User;
import network.NetworkMessage;
import server.ClientHandler;
import service.PayPalService;

import java.util.HashMap;
import java.util.Map;

import static utils.ConsoleColors.*;

public class PaymentHandler implements CommandHandler {

    private final PayPalService payPalService;
    private final ServerPaymentController paymentController;

    // The cache stores pending orders (OrderId -> Amount in VND)
    // TODO: Move those to Database
    private final Map<String, Double> pendingDeposits = new HashMap<>();

    public PaymentHandler() {
        this.payPalService = new PayPalService();
        this.paymentController = new ServerPaymentController();
    }

    @Override
    public void handle(NetworkMessage message, ClientHandler client) {
        String command = message.getCommand();
        Object data = message.getData();

        // Retrieve user information from the login session
        User currentUser = client.getUser();

        // Security check: Only logged-in users can make a deposit
        if (currentUser == null) {
            client.sendResponse("ERROR", "You must log in before making a transaction");
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
                    System.out.println("[PaymentHandler]: Invalid command: " + RED + command + RESET);
                    client.sendResponse("ERROR", "Invalid payment order");
                    break;
            }
        } catch (Exception e) {
            System.out.println("[PaymentHandler]: Error: " + RED + e.getMessage() + RESET);
            client.sendResponse("ERROR", "Server payment processing error: " + e.getMessage());
        }
    }

    /**
     * Process a request from the client to create a deposit form.
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
            client.sendResponse("ERROR", "Deposit amount must be greater than 0");
            return;
        }

        System.out.println("[Payment]: Constructing deposit request of " + YELLOW + amountVND + RESET + " VND for \""
                + YELLOW + currentUser.getName() + RESET + "\"");

        // Call the PayPal API to retrieve the payment URL
        String[] orderInfo = payPalService.createOrder(amountVND);
        String orderId = orderInfo[0];
        String approvalUrl = orderInfo[1];

        // Add to the confirmation waiting list
        pendingDeposits.put(orderId, amountVND);

        // Package the data to be returned to the client
        Map<String, String> responseData = new HashMap<>();
        responseData.put("orderId", orderId);
        responseData.put("url", approvalUrl);

        // Command response to have the client automatically open the browser
        client.sendResponse("PAYMENT_REDIRECT", responseData);
    }

    /**
     * Process the confirmation request after the client reports that payment has been completed on the website.
     */
    private void handleConfirmDeposit(Object data, ClientHandler client, User currentUser) throws Exception {
        String orderId = data.toString().trim();

        // Check if this order is currently in the queue
        if (!pendingDeposits.containsKey(orderId)) {
            client.sendResponse("ERROR", "The transaction code does not exist, has expired, or has already been processed");
            return;
        }

        // Call the PayPal API to capture the payment
        boolean isCaptured = payPalService.captureOrder(orderId);

        if (isCaptured) {
            double amountVND = pendingDeposits.get(orderId);

            // Safely add money to the database (using transactions)
            boolean dbSuccess = paymentController.processDepositSuccess(currentUser, amountVND, orderId);

            if (dbSuccess) {
                client.sendResponse("DEPOSIT_SUCCESS", "Transaction successful. Deposited " + amountVND + " VND");
                pendingDeposits.remove(orderId); // Clear the cache
            } else {
                client.sendResponse("ERROR", "The payment has been deducted from PayPal, but there was an error updating the database. Please contact the admin");
            }
        } else {
            client.sendResponse("ERROR", "The transaction has not been completed or has been canceled on PayPal");
        }
    }
}