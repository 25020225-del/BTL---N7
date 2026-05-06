package controller;

import database.DatabaseManager;
import database.TransactionManager;
import database.dao.WalletDAO;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Controller responsible for handling financial payment operations on the server side.
 */
public class ServerPaymentController {

    private static final Logger log = LoggerFactory.getLogger(ServerPaymentController.class);

    private final WalletDAO walletDAO;

    public ServerPaymentController(WalletDAO walletDAO) {
        this.walletDAO = walletDAO;
    }

    /**
     * Processes a successful deposit notification from a payment gateway (e.g., PayPal).
     */
    public CompletableFuture<Boolean> processDepositSuccess(User user, String payPalOrderId) {
        Callable<Boolean> depositTask = () -> {
            double verifiedAmount = verifyPayPalTransaction(payPalOrderId);

            if (verifiedAmount <= 0) {
                log.warn("Payment verification failed for Order ID: {}", payPalOrderId);
                return false;
            }

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);

                try {
                    String now = LocalDateTime.now().toString();

                    walletDAO.updateBalance(conn, user.getId(), verifiedAmount);

                    walletDAO.addTransaction(
                            conn,
                            "DEP-" + System.currentTimeMillis(),
                            user.getId(),
                            verifiedAmount,
                            "Deposit via PayPal (Order ID: " + payPalOrderId + ")",
                            now
                    );

                    conn.commit();
                    log.info("User {} deposited {} VND (verified)", user.getName(), verifiedAmount);
                    return true;

                } catch (SQLException e) {
                    conn.rollback();
                    log.error("Deposit update error", e);
                    return false;
                }
            } catch (SQLException e) {
                log.error("Lost connection to database during deposit", e);
                return false;
            }
        };

        return TransactionManager.submitTask(depositTask);
    }

    /**
     * Mocks an external PayPal API call to verify the actual amount paid for an order.
     */
    private double verifyPayPalTransaction(String orderId) {
        try {
            Thread.sleep(1000);

            double[] validAmounts = {50000, 100000, 200000, 500000};
            int index = Math.abs(orderId.hashCode()) % validAmounts.length;
            return validAmounts[index];

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }
}
