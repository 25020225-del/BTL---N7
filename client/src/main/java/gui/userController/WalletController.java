package gui.userController;

import client.handler.AuctionEventBus;
import client.handler.ClientPaymentHandler;
import client.network.NetworkService;
import client.service.WalletService;
import gui.process.AlertUtils;
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
import java.util.List;
import java.util.Map;

/**
 * Controller and Custom Node for the Wallet view.
 *
 * <p>Extends {@link VBox} to act as a self-loading custom control via FXML.
 * Manages wallet balance display, deposit initiation (including the
 * TOTP two-factor challenge flow), and the transaction history table.</p>
 *
 * <p><b>Lifecycle:</b> Call {@link #dispose()} when this view is unmounted
 * to prevent EventBus listener leaks.</p>
 */
public class WalletController extends VBox {

    private static final Logger log = LoggerFactory.getLogger(WalletController.class); // FIX: was MainApplication.class

    // ── FXML Components ───────────────────────────────────────────────────────
    @FXML private Label lblTotalBalance;
    @FXML private Label lblFrozenBalance;
    @FXML private TextField txtDepositAmount;
    @FXML private Button btnDeposit;
    @FXML private TextField  txtWithdrawAmount;
    @FXML private ComboBox<String> cmbPayoutMethod;
    @FXML private TextField  txtPayoutDetails;
    @FXML private Button     btnWithdraw;

    @FXML private TableView<WalletTransaction>          tableTransactions;
    @FXML private TableColumn<WalletTransaction, String> colId;
    @FXML private TableColumn<WalletTransaction, String> colDate;
    @FXML private TableColumn<WalletTransaction, Long>   colAmount;
    @FXML private TableColumn<WalletTransaction, String> colDescription;

    // ── State ─────────────────────────────────────────────────────────────────
    private Runnable onReturnAction;
    private long currentBalance       = 0L;
    private long currentFrozenBalance = 0L;

    /** Stores the deposit amount that is awaiting TOTP confirmation so it can be retried. */
    private double pendingDepositAmount = 0.0;

    private final ObservableList<WalletTransaction> transactionData =
            FXCollections.observableArrayList();

    // ── EventBus Listener References (kept for cleanup) ───────────────────────
    private PropertyChangeListener requireTotpListener;
    private PropertyChangeListener invalidTotpListener;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Loads {@code WalletView.fxml} as a custom control rooted at this VBox instance.
     *
     * @throws RuntimeException if the FXML file cannot be found or loaded.
     */
    public WalletController() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/WalletView.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load WalletView.fxml", e);
        }
    }

    // ── FXML Lifecycle ────────────────────────────────────────────────────────

    /**
     * Called by FXMLLoader after all FXML fields are injected.
     * Initializes the table, registers EventBus listeners, and pre-fetches wallet data.
     */
    @FXML
    public void initialize() {
        setupTransactionTable();
        registerEventListeners();
        WalletService.fetchWalletHistory();
        cmbPayoutMethod.getItems().addAll("BANK_TRANSFER", "E_WALLET");
        cmbPayoutMethod.getSelectionModel().selectFirst();
    }
    @FXML
    private void handleWithdraw() {
        // 1. Validate input
        String amountStr    = txtWithdrawAmount.getText().trim();
        String payoutMethod = cmbPayoutMethod.getValue();
        String payoutDetails = txtPayoutDetails.getText().trim();

        if (amountStr.isEmpty() || payoutDetails.isEmpty()) {
            AlertUtils.showError(
                    "Thiếu thông tin",
                    "Vui lòng nhập đầy đủ số tiền và thông tin tài khoản.");
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(amountStr.replace(",", "").replace(".", ""));
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            AlertUtils.showError(
                    "Số tiền không hợp lệ",
                    "Vui lòng nhập số tiền hợp lệ (số nguyên dương).");
            return;
        }

        // 2. Build payload và gửi (không có TOTP lần đầu)
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("amount",        amount);
        payload.put("payoutMethod",  payoutMethod);
        payload.put("payoutDetails", payoutDetails);

        AnimateEffect.pauseNode(btnWithdraw, 3);
        WalletService.requestWithdrawal(amount, payoutMethod, payoutDetails, null);
        log.info("Withdrawal request sent: {} VND via {}", amount, payoutMethod);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sets the callback to invoke when the user presses the "Return" button.
     *
     * @param action The {@link Runnable} to execute on return.
     */
    public void setOnReturnAction(Runnable action) {
        this.onReturnAction = action;
    }

    /**
     * Unregisters all EventBus listeners to prevent memory and event delivery leaks.
     * Call this when the wallet view is hidden or the user session ends.
     */
    public void dispose() {
        if (requireTotpListener != null) {
            AuctionEventBus.removeListener(ClientPaymentHandler.REQUIRE_TOTP_PAYMENT, requireTotpListener);
        }
        if (invalidTotpListener != null) {
            AuctionEventBus.removeListener(ClientPaymentHandler.INVALID_TOTP, invalidTotpListener);
        }
    }

    // ── FXML Event Handlers ───────────────────────────────────────────────────

    @FXML
    private void handleReturn() {
        if (onReturnAction != null) {
            onReturnAction.run();
        }
    }

    /**
     * Validates the deposit amount and initiates the deposit request.
     * Disables the button temporarily to prevent duplicate submissions.
     */
    @FXML
    private void handleDeposit() {
        double amount = parseDepositAmount();
        if (amount <= 0) return;

        AnimateEffect.pauseNode(btnDeposit, 2);
        sendDepositRequest(amount, null);
    }

    /**
     * Parses a quick-amount button click and sets the deposit text field accordingly.
     * Button text format: "+50k", "+100k", "+500k", "+1M".
     *
     * @param event The action event from the quick-amount button.
     */
    @FXML
    private void addQuickAmount(javafx.event.ActionEvent event) {
        Button btn = (Button) event.getSource();
        String text = btn.getText()
                .replace("+", "")
                .replace("k", "000")
                .replace("M", "000000");
        txtDepositAmount.setText(text);
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Configures the {@link TableView} column-to-property bindings.
     */
    private void setupTransactionTable() { // FIX: extracted from initialize() for SRP
        colDate.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        tableTransactions.setItems(transactionData);
    }

    /**
     * Registers all required EventBus listeners for wallet-related server responses.
     */
    private void registerEventListeners() {
        AuctionEventBus.addListener(AuctionEventBus.FETCH_WALLET_SUCCESS, event ->
                handleWalletDataReceived((NetworkMessage) event.getNewValue())
        );

        requireTotpListener = event -> onRequireTotpPayment(event.getNewValue());
        invalidTotpListener = event -> onInvalidTotp(event.getNewValue());

        AuctionEventBus.addListener(AuctionEventBus.DEPOSIT_SUCCESS, event ->
                Platform.runLater(WalletService::fetchWalletHistory)
        );
        AuctionEventBus.addListener(ClientPaymentHandler.REQUIRE_TOTP_PAYMENT, requireTotpListener);
        AuctionEventBus.addListener(ClientPaymentHandler.INVALID_TOTP, invalidTotpListener);

        // ── [NEW] Lắng nghe kết quả rút tiền ────────────────────────────────────
        AuctionEventBus.addListener(ClientPaymentHandler.WITHDRAW_REQUEST_SUCCESS, event ->
                Platform.runLater(() -> {
                    AlertUtils.showInfo(
                            "Yêu cầu đã gửi",
                            "Yêu cầu rút tiền đang chờ Admin duyệt.");
                    txtWithdrawAmount.clear();
                    txtPayoutDetails.clear();
                    WalletService.fetchWalletHistory(); // Refresh balance
                })
        );
        AuctionEventBus.addListener(ClientPaymentHandler.WITHDRAW_APPROVED, event ->
                Platform.runLater(() -> {
                    AlertUtils.showInfo("Withdraw successful", "Please wait while we transfer your money");
                    WalletService.fetchWalletHistory();
                })
        );
        AuctionEventBus.addListener(ClientPaymentHandler.WITHDRAW_REJECTED, event ->
                Platform.runLater(() -> {
                    AlertUtils.showWarning("Withdraw failed", "Your request is rejected.");
                    WalletService.fetchWalletHistory();
                })
        );
    }

    /**
     * Processes the {@code FETCH_WALLET_SUCCESS} event payload, updating the balance
     * display and transaction history table on the JavaFX Application Thread.
     */
    @SuppressWarnings("unchecked")
    private void handleWalletDataReceived(NetworkMessage response) {
        Map<String, Object> map       = (Map<String, Object>) response.getData();
        long balance                  = Long.parseLong(map.get("balance").toString());
        long lockedBalance            = Long.parseLong(map.get("lockedBalance").toString());
        List<Map<String, Object>> txs = (List<Map<String, Object>>) map.get("transactions");

        List<WalletTransaction> newTransactions = txs.stream()
                .map(this::mapToTransaction)
                .toList();

        Platform.runLater(() -> {
            updateBalanceDisplay(balance, lockedBalance);
            transactionData.setAll(newTransactions);
        });
    }

    /**
     * Converts a raw server map into a {@link WalletTransaction} domain object.
     */
    private WalletTransaction mapToTransaction(Map<String, Object> map) {
        WalletTransaction tx = new WalletTransaction(
                map.get("id").toString(),
                "",
                ((Number) map.get("amount")).longValue(),
                map.get("description").toString()
        );
        tx.setCreatedAt(LocalDateTime.parse(
                map.get("createdAt").toString(),
                DateTimeFormatter.ISO_DATE_TIME
        ));
        return tx;
    }

    /**
     * Updates the balance label fields and stores the current values in memory.
     */
    private void updateBalanceDisplay(long balance, long frozenBalance) {
        currentBalance       = balance;
        currentFrozenBalance = frozenBalance;
        lblTotalBalance.setText(String.format("%,d VND", currentBalance));
        lblFrozenBalance.setText(String.format("%,d VND", currentFrozenBalance));
    }

    /**
     * Parses and validates the deposit amount text field.
     *
     * @return A positive {@code double} if valid; {@code -1} if validation fails.
     */
    private double parseDepositAmount() {
        String input = txtDepositAmount.getText().trim();
        try {
            double amount = Double.parseDouble(input);
            if (amount <= 0) throw new NumberFormatException("Amount must be positive");
            return amount;
        } catch (NumberFormatException e) {
            AlertUtils.showError( "Input Error", "Please enter a valid deposit amount.");
            return -1;
        }
    }

    /**
     * Sends the deposit request to the server.
     * Stores the amount as {@link #pendingDepositAmount} for TOTP retry flow.
     *
     * @param amount    The deposit amount in VND.
     * @param totpCode  A 6-digit TOTP code, or {@code null} for the initial attempt.
     */
    private void sendDepositRequest(double amount, String totpCode) {
        pendingDepositAmount = amount;

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("amount", (long) amount);
        if (totpCode != null && !totpCode.isBlank()) {
            payload.put("totpCode", totpCode);
        }

        NetworkService.sendMessage("CREATE_DEPOSIT", payload);
    }

    // ── TOTP Challenge Flow ───────────────────────────────────────────────────

    /**
     * Handles the {@code REQUIRE_TOTP_PAYMENT} event: prompts the user for their
     * TOTP code and retries the deposit if provided.
     */
    @SuppressWarnings("unchecked")
    private void onRequireTotpPayment(Object eventData) {
        Platform.runLater(() -> {
            long serverAmount = resolveAmountFromEvent(eventData);
            String totpCode   = showTotpChallengeDialog(serverAmount);

            NetworkMessage msg = (NetworkMessage) eventData;
            Map<String, Object> data = (Map<String, Object>) msg.getData();
            if (data.containsKey("payoutMethod")) { // Identified as Withdrawal challenge
                String payoutMethod = (String) data.get("payoutMethod");
                String payoutDetails = (String) data.get("payoutDetails");
                Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("amount", serverAmount);
                payload.put("payoutMethod", payoutMethod);
                payload.put("payoutDetails", payoutDetails);
                payload.put("totpCode", totpCode);
                NetworkService.sendMessage("REQUEST_WITHDRAW", payload);
            } else {
                sendDepositRequest(serverAmount, totpCode);
            }
        });
    }

    /**
     * Handles the {@code INVALID_TOTP} event: notifies the user and re-enables the button.
     */
    private void onInvalidTotp(Object eventData) {
        Platform.runLater(() -> {
            AlertUtils.showError(
                    "Invalid TOTP Code",
                    "The 6-digit code you entered is incorrect or has expired.\n"
                            + "Please open Google Authenticator and enter the current code."
            );
            btnDeposit.setDisable(false);
        });
    }

    /**
     * Extracts the deposit amount from the server's TOTP challenge event payload,
     * falling back to the locally cached {@link #pendingDepositAmount}.
     */
    @SuppressWarnings("unchecked")
    private long resolveAmountFromEvent(Object eventData) {
        if (pendingDepositAmount > 0) {
            try {
                NetworkMessage msg          = (NetworkMessage) eventData;
                Map<String, Object> data    = (Map<String, Object>) msg.getData();
                if (data.containsKey("amount")) {
                    return Long.parseLong(data.get("amount").toString());
                }
            } catch (Exception ignored) { /* fall through to default */ }
        }
        return (long) pendingDepositAmount;
    }

    /**
     * Displays a modal dialog requesting the user to enter their TOTP code.
     *
     * @param amount The deposit amount being confirmed (shown for context).
     * @return The 6-digit code string, or {@code null} if the user cancelled.
     */
    private String showTotpChallengeDialog(long amount) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Security Verification");
        dialog.setHeaderText(String.format("🔐 Confirm transaction of %,d VND", amount));

        Label info = new Label(
                "Your account has TOTP protection enabled for transactions.\n"
                        + "Please open Google Authenticator and enter the current 6-digit code."
        );
        info.setWrapText(true);

        TextField otpField = new TextField();
        otpField.setPromptText("Enter 6-digit code...");
        otpField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*") || newVal.length() > 6) {
                otpField.setText(oldVal);
            }
        });

        VBox content = new VBox(10, info, new Label("TOTP Verification Code:"), otpField);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String code = otpField.getText().trim();
                return (code.length() == 6) ? code : null;
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }
}
