package controller;

import database.DatabaseManager;
import database.TransactionManager;
import database.dao.WalletDAO;
import model.user.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static utils.ConsoleColors.*;

/**
 * Controller responsible for handling financial payment operations on the server side.
 * It primarily manages balance deposits and ensures that wallet updates and
 * transaction logging are performed atomically through the {@link TransactionManager}.
 */
public class ServerPaymentController {
    private final WalletDAO walletDAO = new WalletDAO();

    /**
     * Processes a successful deposit notification from a payment gateway (e.g., PayPal).
     * Encapsulates the balance update and history logging into a single ACID transaction.
     *
     * @param user          The user whose wallet will be credited.
     * @param amountVND     The numerical value of the deposit in VND.
     * @param payPalOrderId The external order identifier provided by PayPal for tracking.
     * @return A {@link CompletableFuture} resolving to true if the transaction succeeds.
     */
    public CompletableFuture<Boolean> processDepositSuccess(User user, double amountVND, String payPalOrderId) {
        // Wrap deposit logic into a Task to add to the asynchronous database worker queue
        Callable<Boolean> depositTask = () -> {
            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false); // Start Database Transaction

                try {
                    String now = LocalDateTime.now().toString();

                    // 1. Update the current wallet balance
                    walletDAO.updateBalance(conn, user.getId(), amountVND);

                    // 2. Persist the deposit record into the transaction history
                    walletDAO.addTransaction(
                            conn,
                            "DEP-" + System.currentTimeMillis(),
                            user.getId(),
                            amountVND,
                            "Deposit via PayPal (Order ID: " + payPalOrderId + ")",
                            now
                    );

                    conn.commit(); // Commit all changes as an atomic unit
                    System.out.println("[System]: \"" + YELLOW + user.getName() + RESET + "\" has deposited " + GREEN + amountVND + RESET + " VND");
                    return true;

                } catch (SQLException e) {
                    conn.rollback(); // Undo changes if any step fails
                    System.out.println("[Database]: Deposit update error: " + RED + e.getMessage() + RESET);
                    return false;
                }
            } catch (SQLException e) {
                System.out.println("[Database]: Lost connection to database: " + RED + e.getMessage() + RESET);
                return false;
            }
        };

        // Submit the task to the TransactionManager and wait for the synchronous result
        return TransactionManager.submitTask(depositTask);
    }
}