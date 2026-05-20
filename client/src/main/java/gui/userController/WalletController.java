package gui.userController;

import client.handler.AuctionEventBus;
import client.handler.ClientPaymentHandler;
import client.network.NetworkService;
import client.service.WalletService;
import gui.MainApplication;
import gui.process.AlertHelper;
import gui.process.AnimateEffect;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import model.finance.WalletTransaction;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller and Custom Node (VBox) for the Wallet screen.
 *
 * <p><b>Responsibilities:</b></p>
 * <ul>
 *   <li>Display current balance and frozen (locked) balance.</li>
 *   <li>Handle deposit with optional TOTP Challenge-Response.</li>
 *   <li>Handle withdrawal (Maker step) with optional TOTP Challenge-Response.</li>
 *   <li>Display the transaction history table.</li>
 *   <li>Listen for real-time {@code WITHDRAW_APPROVED} / {@code WITHDRAW_REJECTED}
 *       notifications from the server.</li>
 * </ul>
 *
 * <p><b>Threading model:</b> All EventBus listeners receive events on the network
 * thread. Any JavaFX scene graph mutation MUST be wrapped in
 * {@link Platform#runLater(Runnable)}.</p>
 *
 * <p><b>Lifecycle:</b> Call {@link #dispose()} when this node is removed from the
 * scene to prevent memory leaks in the EventBus.</p>
 */
public class WalletController extends VBox {

    private static final Logger log = LoggerFactory.getLogger(WalletController.class);

    // ── Callback ──────────────────────────────────────────────────────────────
    private Runnable onReturnAction;

    // ── FXML — Deposit ────────────────────────────────────────────────────────
    @FXML private Label lblTotalBalance;
    @FXML private Label lblFrozenBalance;
    @FXML private TextField txtDepositAmount;
    @FXML private Button btnDeposit;

    // ── FXML — Withdrawal [NEW] ───────────────────────────────────────────────
    /** Amount the user wishes to withdraw (in VND). */
    @FXML private TextField txtWithdrawAmount;

    /** Dropdown: "BANK_TRANSFER", "MOMO", "ZALOPAY". */
    @FXML private ComboBox<String> cmbPayoutMethod;

    /** Bank name, account number, account holder name, etc. */
    @FXML private TextField txtPayoutDetails;

    /** Triggers the withdrawal request. */
    @FXML private Button btnWithdraw;

    // ── FXML — Transaction Table ──────────────────────────────────────────────
    @FXML private TableView<WalletTransaction> tableTransactions;
    @FXML private TableColumn<WalletTransaction, String> colId;
    @FXML private TableColumn<WalletTransaction, String> colDate;
    @FXML private TableColumn<WalletTransaction, Long>   colAmount;
    @FXML private TableColumn<WalletTransaction, String> colDescription;

    // ── State ─────────────────────────────────────────────────────────────────
    private long currentBalance       = 0L;
    private long currentFrozenBalance = 0L;
    private final ObservableList<WalletTransaction> transactionData =
            FXCollections.observableArrayList();

    // ── Pending TOTP state ────────────────────────────────────────────────────
    /** Stores the pending deposit amount while waiting for TOTP input. */
    private long pendingDepositAmount = 0L;

    /** Stores the full pending withdrawal payload while waiting for TOTP input. */
    private long   pendingWithdrawAmount  = 0L;
    private String pendingPayoutMethod    = null;
    private String pendingPayoutDetails   = null;

    // ── EventBus listeners (kept as fields to allow removal in dispose()) ─────
    private PropertyChangeListener walletFetchListener;
    private PropertyChangeListener requireTotpListener;
    private PropertyChangeListener invalidTotpListener;
    private PropertyChangeListener withdrawSuccessListener;
    private PropertyChangeListener withdrawApprovedListener;
    private PropertyChangeListener withdrawRejectedListener;

    // ─────────────────────────────────────────────────────────────────────────
    // CONSTRUCTOR
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads {@code WalletView.fxml} as a custom control embedded in this VBox.
     *
     * @throws RuntimeException if the FXML file cannot be loaded.
     */
    public WalletController() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/gui/WalletView.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try {
            fxmlLoader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load WalletView.fxml", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INITIALIZATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Initializes table columns, populates the payout method list,
     * and registers all required EventBus listeners.
     *
     * <p>Called automatically by FXMLLoader after injection of @FXML fields.</p>
     */
    @FXML
    public void initialize() {
        // ── Table setup ───────────────────────────────────────────────────────
        colDate.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        tableTransactions.setItems(transactionData);

        // ── Withdrawal form setup ─────────────────────────────────────────────
        if (cmbPayoutMethod != null) {
            cmbPayoutMethod.setItems(
                    FXCollections.observableArrayList("BANK_TRANSFER", "MOMO", "ZALOPAY"));
            cmbPayoutMethod.getSelectionModel().selectFirst();
        }

        // ── EventBus listeners ────────────────────────────────────────────────
        walletFetchListener = event -> onWalletFetched(event.getNewValue());
        requireTotpListener  = event -> onRequireTotpPayment(event.getNewValue());
        invalidTotpListener  = event -> onInvalidTotp(event.getNewValue());
        withdrawSuccessListener  = event -> onWithdrawRequestSuccess(event.getNewValue());
        withdrawApprovedListener = event -> onWithdrawApproved(event.getNewValue());
        withdrawRejectedListener = event -> onWithdrawRejected(event.getNewValue());

        AuctionEventBus.addListener("FETCH_WALLET_SUCCESS",               walletFetchListener);
        AuctionEventBus.addListener(ClientPaymentHandler.REQUIRE_TOTP_PAYMENT, requireTotpListener);
        AuctionEventBus.addListener(ClientPaymentHandler.INVALID_TOTP,         invalidTotpListener);
        AuctionEventBus.addListener(ClientPaymentHandler.WITHDRAW_REQUEST_SUCCESS, withdrawSuccessListener);
        AuctionEventBus.addListener(ClientPaymentHandler.WITHDRAW_APPROVED,   withdrawApprovedListener);
        AuctionEventBus.addListener(ClientPaymentHandler.WITHDRAW_REJECTED,   withdrawRejectedListener);

        WalletService.fetchWalletHistory();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /** Sets the callback to invoke when the user clicks "Back". */
    public void setOnReturnAction(Runnable action) {
        this.onReturnAction = action;
    }

    /**
     * Updates the displayed available balance.
     *
     * @param balance New available balance in VND.
     */
    public void setWalletBalance(long balance) {
        currentBalance = balance;
        lblTotalBalance.setText(balance + " VND");
    }

    /**
     * Updates the displayed locked (frozen) balance.
     *
     * @param lockedBalance New locked balance in VND.
     */
    public void setWalletLockedBalance(long lockedBalance) {
        currentFrozenBalance = lockedBalance;
        lblFrozenBalance.setText(lockedBalance + " VND");
    }

    /**
     * Removes all EventBus listeners registered by this controller.
     *
     * <p>MUST be called when this node is removed from the scene to prevent
     * memory leaks caused by stale listener references in the EventBus.</p>
     */
    public void dispose() {
        AuctionEventBus.removeListener("FETCH_WALLET_SUCCESS",               walletFetchListener);
        AuctionEventBus.removeListener(ClientPaymentHandler.REQUIRE_TOTP_PAYMENT, requireTotpListener);
        AuctionEventBus.removeListener(ClientPaymentHandler.INVALID_TOTP,         invalidTotpListener);
        AuctionEventBus.removeListener(ClientPaymentHandler.WITHDRAW_REQUEST_SUCCESS, withdrawSuccessListener);
        AuctionEventBus.removeListener(ClientPaymentHandler.WITHDRAW_APPROVED,   withdrawApprovedListener);
        AuctionEventBus.removeListener(ClientPaymentHandler.WITHDRAW_REJECTED,   withdrawRejectedListener);
        log.debug("WalletController disposed: all EventBus listeners removed.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FXML HANDLERS
    // ─────────────────────────────────────────────────────────────────────────

    /** Navigates back to the previous screen. */
    @FXML
    private void handleReturn() {
        if (onReturnAction != null) {
            onReturnAction.run();
        }
    }

    /** Handles the Deposit button click. Validates input and triggers deposit flow. */
    @FXML
    private void handleDeposit() {
        long amount = parsePositiveLong(txtDepositAmount.getText());
        if (amount <= 0) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Lỗi", "Vui lòng nhập số tiền hợp lệ (> 0)!");
            return;
        }
        AnimateEffect.pauseNode(btnDeposit, 2);
        sendDepositRequest(amount, null);
    }

    /**
     * Handles the Withdraw button click.
     * Validates all withdrawal form fields and triggers the withdrawal flow.
     */
    @FXML
    private void handleWithdraw() {
        // ── Validate amount ───────────────────────────────────────────────────
        long amount = parsePositiveLong(txtWithdrawAmount.getText());
        if (amount <= 0) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Lỗi",
                    "Vui lòng nhập số tiền rút hợp lệ (> 0 VND)!");
            return;
        }

        // ── Validate payout method ────────────────────────────────────────────
        String payoutMethod = cmbPayoutMethod.getValue();
        if (payoutMethod == null || payoutMethod.isBlank()) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Lỗi",
                    "Vui lòng chọn phương thức nhận tiền!");
            return;
        }

        // ── Validate payout details ───────────────────────────────────────────
        String payoutDetails = txtPayoutDetails.getText().trim();
        if (payoutDetails.isBlank()) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Lỗi",
                    "Vui lòng nhập thông tin tài khoản nhận tiền!");
            return;
        }

        // ── Optimistic balance check (server-side check is authoritative) ─────
        if (amount > currentBalance) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Số dư không đủ",
                    String.format("Số dư khả dụng (%,d VND) không đủ để rút %,d VND.",
                            currentBalance, amount));
            return;
        }

        AnimateEffect.pauseNode(btnWithdraw, 3);
        sendWithdrawRequest(amount, payoutMethod, payoutDetails, null);
    }

    /** Quick-amount button ("+50k", "+200k", "+1M") sets the deposit text field. */
    @FXML
    private void addQuickAmount(javafx.event.ActionEvent event) {
        Button btn = (Button) event.getSource();
        String text = btn.getText()
                .replace("+", "")
                .replace("k", "000")
                .replace("M", "000000");
        txtDepositAmount.setText(text);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EVENTBUS RESPONSE HANDLERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Processes the {@code FETCH_WALLET_SUCCESS} response from the server.
     *
     * <p><b>Threading:</b> This method is invoked on the network thread.
     * Data preparation runs off the FX thread; all scene graph mutations are
     * deferred to {@link Platform#runLater(Runnable)}.</p>
     *
     * @param eventData The raw event payload (a {@link NetworkMessage}).
     */
    @SuppressWarnings("unchecked")
    private void onWalletFetched(Object eventData) {
        try {
            NetworkMessage response = (NetworkMessage) eventData;
            Object rawData = response.getData();
            if (rawData == null) {
                log.warn("FETCH_WALLET_SUCCESS: received null data.");
                return;
            }

            Map<String, Object> map = (Map<String, Object>) rawData;

            Object balanceObj = map.get("balance");
            Object lockedObj  = map.get("lockedBalance");
            if (balanceObj == null || lockedObj == null) {
                log.warn("FETCH_WALLET_SUCCESS: missing balance keys in payload.");
                return;
            }

            long balance       = ((Number) balanceObj).longValue();
            long lockedBalance = ((Number) lockedObj).longValue();

            // Build the transaction list OFF the FX thread to avoid blocking UI.
            List<Map<String, Object>> rawTx =
                    (List<Map<String, Object>>) map.getOrDefault("transactions", List.of());

            List<WalletTransaction> newTx = new ArrayList<>(rawTx.size());
            for (Map<String, Object> tx : rawTx) {
                WalletTransaction wt = new WalletTransaction(
                        tx.get("id").toString(),
                        "",
                        ((Number) tx.get("amount")).longValue(),
                        tx.get("description").toString()
                );
                wt.setCreatedAt(LocalDateTime.parse(
                        tx.get("createdAt").toString(),
                        DateTimeFormatter.ISO_DATE_TIME));
                newTx.add(wt);
            }

            // All scene-graph mutations MUST be on the FX thread.
            Platform.runLater(() -> {
                setWalletBalance(balance);
                setWalletLockedBalance(lockedBalance);
                transactionData.setAll(newTx); // atomic replace — thread-safe on FX thread
            });

        } catch (Exception e) {
            log.error("Error processing FETCH_WALLET_SUCCESS response", e);
        }
    }

    /**
     * Called when the server sends {@code REQUIRE_TOTP_PAYMENT}.
     *
     * <p>Determines whether the pending operation was a deposit or a withdrawal
     * by checking which pending state fields are populated, then shows the
     * appropriate TOTP dialog and retries.</p>
     *
     * @param eventData The raw event payload (a {@link NetworkMessage}).
     */
    @SuppressWarnings("unchecked")
    private void onRequireTotpPayment(Object eventData) {
        Platform.runLater(() -> {
            long serverAmount = 0L;
            String payoutMethod  = null;
            String payoutDetails = null;

            // Try to extract echo-ed data from server response.
            try {
                NetworkMessage msg = (NetworkMessage) eventData;
                Map<String, Object> data = (Map<String, Object>) msg.getData();
                if (data.containsKey("amount")) {
                    serverAmount = ((Number) data.get("amount")).longValue();
                }
                if (data.containsKey("payoutMethod")) {
                    payoutMethod = (String) data.get("payoutMethod");
                }
                if (data.containsKey("payoutDetails")) {
                    payoutDetails = (String) data.get("payoutDetails");
                }
            } catch (Exception ignored) {
                // Fall back to locally-stored pending state below.
            }

            // Determine if this challenge is for withdrawal or deposit.
            boolean isWithdrawal = (pendingPayoutMethod != null);

            if (isWithdrawal) {
                // Use server-echoed values or fall back to local state.
                long amount = serverAmount > 0 ? serverAmount : pendingWithdrawAmount;
                String method  = payoutMethod  != null ? payoutMethod  : pendingPayoutMethod;
                String details = payoutDetails != null ? payoutDetails : pendingPayoutDetails;

                String totpCode = showTotpChallengeDialog(amount, "rút");
                if (totpCode != null) {
                    sendWithdrawRequest(amount, method, details, totpCode);
                } else {
                    log.info("User cancelled TOTP challenge for withdrawal.");
                    if (btnWithdraw != null) btnWithdraw.setDisable(false);
                }
            } else {
                // Deposit flow.
                long amount = serverAmount > 0 ? serverAmount : pendingDepositAmount;
                String totpCode = showTotpChallengeDialog(amount, "nạp");
                if (totpCode != null) {
                    sendDepositRequest(amount, totpCode);
                } else {
                    log.info("User cancelled TOTP challenge for deposit.");
                    if (btnDeposit != null) btnDeposit.setDisable(false);
                }
            }
        });
    }

    /**
     * Called when the server sends {@code INVALID_TOTP}.
     * Displays an error alert and re-enables the relevant submit button.
     *
     * @param eventData Ignored (the alert message is hard-coded for UX clarity).
     */
    private void onInvalidTotp(Object eventData) {
        Platform.runLater(() -> {
            AlertHelper.showAlert(
                    Alert.AlertType.ERROR,
                    "Mã TOTP không hợp lệ",
                    "Mã 6 số bạn nhập không đúng hoặc đã hết hạn.\n"
                            + "Vui lòng mở Google Authenticator và nhập mã mới."
            );
            // Re-enable whichever button was active.
            if (pendingPayoutMethod != null) {
                if (btnWithdraw != null) btnWithdraw.setDisable(false);
            } else {
                if (btnDeposit != null) btnDeposit.setDisable(false);
            }
        });
    }

    /**
     * Called when the server sends {@code WITHDRAW_REQUEST_SUCCESS}.
     * Shows confirmation, refreshes the wallet, and clears the form.
     *
     * @param eventData The raw event payload (a {@link NetworkMessage}).
     */
    @SuppressWarnings("unchecked")
    private void onWithdrawRequestSuccess(Object eventData) {
        Platform.runLater(() -> {
            String msg = "Yêu cầu rút tiền đã được ghi nhận và đang chờ Admin duyệt.";
            try {
                NetworkMessage nm = (NetworkMessage) eventData;
                Map<String, Object> data = (Map<String, Object>) nm.getData();
                if (data.containsKey("message")) {
                    msg = data.get("message").toString();
                }
            } catch (Exception ignored) { /* Use default message */ }

            AlertHelper.showAlert(Alert.AlertType.INFORMATION, "Yêu Cầu Rút Tiền", msg);

            // Clear pending state and form.
            clearWithdrawForm();
            if (btnWithdraw != null) btnWithdraw.setDisable(false);

            // Refresh wallet to reflect updated balance.
            WalletService.fetchWalletHistory();
        });
    }

    /**
     * Called when the server sends real-time {@code WITHDRAW_APPROVED}.
     * Notifies the user and refreshes the wallet balance.
     *
     * @param eventData The raw event payload (a {@link NetworkMessage}).
     */
    @SuppressWarnings("unchecked")
    private void onWithdrawApproved(Object eventData) {
        Platform.runLater(() -> {
            AlertHelper.showAlert(Alert.AlertType.INFORMATION,
                    "Rút Tiền Thành Công",
                    "Yêu cầu rút tiền của bạn đã được Admin duyệt.\n"
                            + "Tiền đã được chuyển ra khỏi hệ thống.");
            WalletService.fetchWalletHistory();
        });
    }

    /**
     * Called when the server sends real-time {@code WITHDRAW_REJECTED}.
     * Notifies the user that their balance has been refunded.
     *
     * @param eventData The raw event payload (a {@link NetworkMessage}).
     */
    @SuppressWarnings("unchecked")
    private void onWithdrawRejected(Object eventData) {
        Platform.runLater(() -> {
            AlertHelper.showAlert(Alert.AlertType.WARNING,
                    "Yêu Cầu Bị Từ Chối",
                    "Yêu cầu rút tiền của bạn đã bị Admin từ chối.\n"
                            + "Số tiền đã được hoàn lại vào số dư khả dụng.");
            WalletService.fetchWalletHistory();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends a deposit request to the server, storing {@code amount} in
     * {@link #pendingDepositAmount} and clearing any pending withdrawal state.
     *
     * @param amount   Amount in VND.
     * @param totpCode 6-digit TOTP, or {@code null}.
     */
    private void sendDepositRequest(long amount, String totpCode) {
        // Mark that the pending operation is a deposit (not a withdrawal).
        pendingDepositAmount = amount;
        clearWithdrawPendingState();
        WalletService.createDeposit(amount, totpCode);
    }

    /**
     * Sends a withdrawal request to the server, storing all payload fields so
     * that the TOTP retry can reconstruct the exact same request.
     *
     * @param amount        Amount in VND.
     * @param payoutMethod  Payment method string.
     * @param payoutDetails Account details string.
     * @param totpCode      6-digit TOTP, or {@code null}.
     */
    private void sendWithdrawRequest(long amount,
                                     String payoutMethod,
                                     String payoutDetails,
                                     String totpCode) {
        // Store pending state so TOTP retry can re-send exactly the same request.
        pendingWithdrawAmount = amount;
        pendingPayoutMethod   = payoutMethod;
        pendingPayoutDetails  = payoutDetails;
        pendingDepositAmount  = 0L; // clear deposit state

        WalletService.requestWithdrawal(amount, payoutMethod, payoutDetails, totpCode);
    }

    /**
     * Shows a modal dialog asking the user to enter their 6-digit TOTP code.
     *
     * @param amount      The transaction amount (shown for context).
     * @param actionLabel Either "nạp" or "rút" (shown in the dialog header).
     * @return The entered 6-digit code string, or {@code null} if cancelled.
     */
    private String showTotpChallengeDialog(long amount, String actionLabel) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Xác Thực Bảo Mật");
        dialog.setHeaderText(String.format("🔐 Xác nhận giao dịch %s %,d VND", actionLabel, amount));

        Label info = new Label(
                "Tài khoản của bạn đã bật bảo vệ TOTP cho giao dịch.\n"
                        + "Hãy mở Google Authenticator và nhập mã 6 số hiện tại.");
        info.setWrapText(true);

        TextField otpField = new TextField();
        otpField.setPromptText("Nhập mã 6 số...");
        otpField.textProperty().addListener((obs, oldVal, newVal) -> {
            // Only allow digits, max 6 characters.
            if (!newVal.matches("\\d*") || newVal.length() > 6) {
                otpField.setText(oldVal);
            }
        });

        VBox content = new VBox(10, info, new Label("Mã xác thực TOTP:"), otpField);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String code = otpField.getText().trim();
                return (code.length() == 6) ? code : null;
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        return result.orElse(null);
    }

    /** Clears the pending withdrawal state (method and details). */
    private void clearWithdrawPendingState() {
        pendingWithdrawAmount = 0L;
        pendingPayoutMethod   = null;
        pendingPayoutDetails  = null;
    }

    /** Clears the withdrawal form fields and resets pending state. */
    private void clearWithdrawForm() {
        clearWithdrawPendingState();
        if (txtWithdrawAmount != null) txtWithdrawAmount.clear();
        if (txtPayoutDetails != null)  txtPayoutDetails.clear();
    }

    /**
     * Parses a text field string into a positive {@code long}.
     *
     * @param text The raw text input.
     * @return The parsed value, or {@code -1} if the input is blank or invalid.
     */
    private long parsePositiveLong(String text) {
        try {
            long value = Long.parseLong(text.trim());
            return value > 0 ? value : -1L;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}