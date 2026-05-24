package controller;

import database.DatabaseManager;
import database.TransactionManager;
import database.dao.WalletDAO;
import database.dao.WithdrawalDAO;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Controller executing financial processing states. Ensures database wallet accounting rules
 * conform to strict isolation properties when issuing atomic digital asset mutations.
 */
public class ServerPaymentController {

    private static final Logger log = LoggerFactory.getLogger(ServerPaymentController.class);
    private final WalletDAO walletDAO;
    private final WithdrawalDAO withdrawalDAO;

    public ServerPaymentController(WalletDAO walletDAO, WithdrawalDAO withdrawalDAO) {
        this.walletDAO = walletDAO;
        this.withdrawalDAO = withdrawalDAO;
    }

    public CompletableFuture<Boolean> processDepositSuccess(User user, String payPalOrderId, long verifiedAmount) {
        Callable<Boolean> depositTask = () -> {
            if (verifiedAmount <= 0) return false;

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    String now = LocalDateTime.now().toString();
                    walletDAO.updateBalance(conn, user.getId(), verifiedAmount);

                    // Uses payPalOrderId directly as transaction alternate key to enforce constraint level double-spending bars.
                    walletDAO.addTransaction(conn, "DEP-" + payPalOrderId, user.getId(), verifiedAmount,
                            "Deposit via PayPal (Order ID: " + payPalOrderId + ")", now);

                    conn.commit();
                    return true;
                } catch (SQLException e) {
                    conn.rollback();
                    log.error("Deposit update error: {}", e.getMessage());
                    return false;
                }
            } catch (SQLException e) {
                log.error("Lost connection to database: {}", e.getMessage());
                return false;
            }
        };
        return TransactionManager.submitTask(depositTask);
    }

    public CompletableFuture<String> createWithdrawalRequest(User user, long amount, String payoutMethod, String payoutDetails) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture("INVALID_AMOUNT");
        }

        Callable<String> withdrawTask = () -> {
            String requestId = utils.IdGenerator.generateSecureShortId("WD-", 8);
            String now = LocalDateTime.now().toString();

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    if (!walletDAO.deductBalance(conn, user.getId(), amount)) {
                        conn.rollback();
                        return "INSUFFICIENT_FUNDS";
                    }

                    String lockSql = "UPDATE wallets SET locked_balance = locked_balance + ? WHERE user_id = ?";
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(lockSql)) {
                        ps.setLong(1, amount);
                        ps.setString(2, user.getId());
                        if (ps.executeUpdate() == 0) {
                            conn.rollback();
                            return "DB_ERROR";
                        }
                    }

                    if (!withdrawalDAO.createRequest(conn, requestId, user.getId(), amount, payoutMethod, payoutDetails, now)) {
                        conn.rollback();
                        return "DB_ERROR";
                    }

                    walletDAO.addTransaction(conn, "WD-LOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                            user.getId(), -amount, "Withdrawal request pending Admin approval (Request ID: " + requestId + ")", now);

                    conn.commit();
                    return "SUCCESS";

                } catch (SQLException e) {
                    conn.rollback();
                    log.error("[WITHDRAW] SQL error creating request for user {}: {}", user.getUserName(), e.getMessage(), e);
                    return "DB_ERROR";
                }
            } catch (SQLException e) {
                log.error("[WITHDRAW] DB connection error for user {}: {}", user.getUserName(), e.getMessage(), e);
                return "DB_ERROR";
            }
        };

        return TransactionManager.submitTask(withdrawTask);
    }
}