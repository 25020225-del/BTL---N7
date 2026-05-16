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
 * It primarily manages balance deposits and ensures that wallet updates and
 * transaction logging are performed atomically through the TransactionManager.
 */
public class ServerPaymentController {
    private static final Logger log = LoggerFactory.getLogger(ServerPaymentController.class);

    private final WalletDAO walletDAO;

    /**
     * Constructs the controller with the necessary Data Access Objects.
     *
     * @param walletDAO The DAO responsible for wallet-related database transactions.
     */
    public ServerPaymentController(WalletDAO walletDAO) {
        this.walletDAO = walletDAO;
    }

    /**
     * Processes a successful deposit notification from a payment gateway.
     * Encapsulates the balance update and history logging into a single ACID transaction.
     *
     * @param user           The user whose wallet will be credited.
     * @param payPalOrderId  The external order identifier provided by PayPal for tracking.
     * @param verifiedAmount The actual verified amount retrieved from the PayPal API.
     * @return A CompletableFuture resolving to true if the transaction succeeds.
     */
    public CompletableFuture<Boolean> processDepositSuccess(User user, String payPalOrderId, long verifiedAmount) {
        // Wrap deposit logic into a Task to add to the asynchronous database worker queue
        Callable<Boolean> depositTask = () -> {
            // Verify the amount before processing the database transaction
            if (verifiedAmount <= 0) {
                log.warn("Payment verification failed for Order ID: {}", payPalOrderId);
                return false;
            }

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    String now = LocalDateTime.now().toString();

                    // 1. Update the current wallet balance
                    walletDAO.updateBalance(conn, user.getId(), verifiedAmount);

                    // 2. Persist the deposit record into the transaction history
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
                    // Undo changes if any step fails
                    log.error("Deposit update error: {}", e.getMessage());
                    return false;
                }
            } catch (SQLException e) {
                log.error("Lost connection to database: {}", e.getMessage());
                return false;
            }
        };
        // Submit the task to the TransactionManager and wait for the synchronous result
        return TransactionManager.submitTask(depositTask);
    }
}