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
 * Controller chịu trách nhiệm xử lý tất cả các nghiệp vụ tài chính phía server.
 *
 * <p>Bao gồm:</p>
 * <ul>
 *   <li>Nạp tiền ({@link #processDepositSuccess}) — thông qua PayPal.</li>
 *   <li>Rút tiền ({@link #createWithdrawalRequest}) — theo mô hình Maker-Checker.</li>
 * </ul>
 *
 * <p><b>Bất biến quan trọng (Financial Invariants):</b></p>
 * <ul>
 *   <li>Mọi thao tác tiền tệ PHẢI được bọc trong {@link TransactionManager#submitTask}
 *       để đảm bảo chạy trên DB Worker Thread và không tranh chấp với nhau.</li>
 *   <li>Bên trong mỗi task, tất cả bước thay đổi số dư PHẢI nằm trong một
 *       JDBC transaction duy nhất ({@code conn.setAutoCommit(false)}) để đảm bảo
 *       tính nguyên tử — hoặc tất cả thành công, hoặc tất cả rollback.</li>
 * </ul>
 */
public class ServerPaymentController {

    private static final Logger log = LoggerFactory.getLogger(ServerPaymentController.class);

    private final WalletDAO walletDAO;
    private final WithdrawalDAO withdrawalDAO;

    /**
     * Constructs the controller with necessary DAOs via Dependency Injection.
     *
     * @param walletDAO     DAO quản lý ví và số dư.
     * @param withdrawalDAO DAO quản lý yêu cầu rút tiền.
     */
    public ServerPaymentController(WalletDAO walletDAO, WithdrawalDAO withdrawalDAO) {
        this.walletDAO = walletDAO;
        this.withdrawalDAO = withdrawalDAO;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEPOSIT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Processes a successful deposit notification from a payment gateway.
     * Encapsulates the balance update and history logging into a single ACID transaction.
     *
     * @param user           The user whose wallet will be credited.
     * @param payPalOrderId  The external order identifier provided by PayPal for tracking.
     * @param verifiedAmount The actual verified amount retrieved from the PayPal API.
     * @return A {@link CompletableFuture} resolving to true if the transaction succeeds.
     */
    public CompletableFuture<Boolean> processDepositSuccess(User user, String payPalOrderId, long verifiedAmount) {
        Callable<Boolean> depositTask = () -> {
            if (verifiedAmount <= 0) {
                log.warn("Payment verification failed for Order ID: {}", payPalOrderId);
                return false;
            }

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    String now = LocalDateTime.now().toString();

                    walletDAO.updateBalance(conn, user.getId(), verifiedAmount);

                    // [SECURITY FIX]: Use payPalOrderId as the transaction ID to enforce DB-level uniqueness.
                    // This strictly prevents Double-Spending if multiple concurrent requests are made for the same order.
                    walletDAO.addTransaction(
                            conn,
                            "DEP-" + payPalOrderId,
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

    // ─────────────────────────────────────────────────────────────────────────
    // WITHDRAWAL — Maker Step (User tạo yêu cầu)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tạo một yêu cầu rút tiền mới theo mô hình Maker-Checker.
     *
     * <p><b>Luồng nghiệp vụ (3 bước ACID trong 1 transaction):</b></p>
     * <ol>
     *   <li>Trừ {@code amount} khỏi {@code wallets.balance} của user
     *       (dùng guard {@code balance >= amount} để ngăn số âm).</li>
     *   <li>Cộng {@code amount} vào {@code wallets.locked_balance}
     *       (tiền bị giữ chờ Admin duyệt — user không dùng được).</li>
     *   <li>Insert bản ghi vào {@code withdrawal_requests} với trạng thái {@code PENDING}.</li>
     * </ol>
     *
     * <p>Nếu bất kỳ bước nào thất bại, toàn bộ transaction sẽ rollback —
     * số dư của user không thay đổi và không có yêu cầu nào được tạo ra.
     * Điều này đảm bảo <b>không bao giờ mất tiền</b>.</p>
     *
     * @param user          User đang yêu cầu rút tiền (đã được xác thực).
     * @param amount        Số tiền muốn rút (VND, phải > 0).
     * @param payoutMethod  Phương thức nhận tiền (vd: "BANK_TRANSFER", "MOMO").
     * @param payoutDetails Thông tin tài khoản nhận (vd: "Ngân hàng: VCB | STK: 1234567890").
     * @return {@link CompletableFuture} chứa một trong các kết quả:
     * <ul>
     *   <li>{@code "SUCCESS"}           — Yêu cầu đã được tạo thành công.</li>
     *   <li>{@code "INSUFFICIENT_FUNDS"} — Số dư khả dụng không đủ.</li>
     *   <li>{@code "DB_ERROR"}           — Lỗi hệ thống cơ sở dữ liệu.</li>
     * </ul>
     */
    public CompletableFuture<String> createWithdrawalRequest(User user,
                                                             long amount,
                                                             String payoutMethod,
                                                             String payoutDetails) {
        // [SECURITY]: Validate đầu vào ngay trên luồng chính trước khi đưa vào Queue
        if (amount <= 0) {
            return CompletableFuture.completedFuture("INVALID_AMOUNT");
        }

        Callable<String> withdrawTask = () -> {
            String requestId = utils.IdGenerator.generateSecureShortId("WD-", 8);
            String now = LocalDateTime.now().toString();

            try (Connection conn = DatabaseManager.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // ── BƯỚC 1: Trừ balance (có guard balance >= amount) ───────────
                    boolean deducted = walletDAO.deductBalance(conn, user.getId(), amount);
                    if (!deducted) {
                        conn.rollback();
                        log.warn("[WITHDRAW] Insufficient funds for user {} (amount={})",
                                user.getUserName(), amount);
                        return "INSUFFICIENT_FUNDS";
                    }

                    // ── BƯỚC 2: Chuyển tiền sang locked_balance ───────────────────
                    // Ghi trực tiếp vào locked_balance thay vì gọi lockBalance()
                    // vì balance đã được trừ ở bước 1 — tránh double-check và race condition.
                    String lockSql = "UPDATE wallets SET locked_balance = locked_balance + ? "
                            + "WHERE user_id = ?";
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(lockSql)) {
                        ps.setLong(1, amount);
                        ps.setString(2, user.getId());
                        int affected = ps.executeUpdate();
                        if (affected == 0) {
                            conn.rollback();
                            log.error("[WITHDRAW] Wallet not found for user {}", user.getUserName());
                            return "DB_ERROR";
                        }
                    }

                    // ── BƯỚC 3: Ghi yêu cầu vào withdrawal_requests ──────────────
                    boolean inserted = withdrawalDAO.createRequest(
                            conn, requestId, user.getId(), amount,
                            payoutMethod, payoutDetails, now
                    );
                    if (!inserted) {
                        conn.rollback();
                        log.error("[WITHDRAW] Failed to insert withdrawal request for user {}",
                                user.getUserName());
                        return "DB_ERROR";
                    }
                    walletDAO.addTransaction(
                            conn,
                            "WD-LOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                            user.getId(),
                            -amount,  // âm: tiền bị khóa (rời khỏi available balance)
                            "Withdrawal request pending Admin approval (Request ID: " + requestId + ")",
                            now
                    );

                    // ── COMMIT khi cả 3 bước đều thành công ──────────────────────
                    conn.commit();
                    log.info("[WITHDRAW] Request {} created: user={}, amount={}",
                            requestId, user.getUserName(), amount);
                    return "SUCCESS";

                } catch (SQLException e) {
                    conn.rollback();
                    log.error("[WITHDRAW] SQL error creating request for user {}: {}",
                            user.getUserName(), e.getMessage(), e);
                    return "DB_ERROR";
                }
            } catch (SQLException e) {
                log.error("[WITHDRAW] DB connection error for user {}: {}",
                        user.getUserName(), e.getMessage(), e);
                return "DB_ERROR";
            }
        };

        return TransactionManager.submitTask(withdrawTask);
    }
}