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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Composite node controller wrapping financial account behaviors. Houses ledger presentation listings,
 * routes fund allocations, and coordinates the secure multi-stage two-factor challenge (TOTP Protocol) handshake.
 */
public class WalletController extends VBox {

    private static final Logger log = LoggerFactory.getLogger(WalletController.class);

    @FXML private Label lblTotalBalance;
    @FXML private Label lblFrozenBalance;
    @FXML private TextField txtDepositAmount;
    @FXML private Button btnDeposit;
    @FXML private TextField txtWithdrawAmount;
    @FXML private ComboBox<String> cmbPayoutMethod;
    @FXML private TextField txtPayoutDetails;
    @FXML private Button btnWithdraw;
    @FXML private TableView<WalletTransaction> tableTransactions;
    @FXML private TableColumn<WalletTransaction, String> colId;
    @FXML private TableColumn<WalletTransaction, String> colDate;
    @FXML private TableColumn<WalletTransaction, Long> colAmount;
    @FXML private TableColumn<WalletTransaction, String> colDescription;

    private Runnable onReturnAction;
    private long currentBalance = 0L;
    private long currentFrozenBalance = 0L;
    private long pendingDepositAmount = 0L;

    private final ObservableList<WalletTransaction> transactionData = FXCollections.observableArrayList();

    private PropertyChangeListener requireTotpListener;
    private PropertyChangeListener invalidTotpListener;

    /**
     * Binds the custom control root layout schema and updates instance variables fields.
     */
    public WalletController() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/WalletView.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Critical failure mapping ledger layout schema files into memory", e);
        }
    }

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
        String amountStr = txtWithdrawAmount.getText().trim();
        String payoutMethod = cmbPayoutMethod.getValue();
        String payoutDetails = txtPayoutDetails.getText().trim();

        if (amountStr.isEmpty() || payoutDetails.isEmpty()) {
            AlertUtils.showError("Thiếu thông tin", "Vui lòng nhập đầy đủ số tiền và thông tin tài khoản.");
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(amountStr.replace(",", "").replace(".", ""));
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            AlertUtils.showError("Số tiền không hợp lệ", "Vui lòng nhập số tiền hợp lệ (số nguyên dương).");
            return;
        }

        AnimateEffect.pauseNode(btnWithdraw, 3);
        WalletService.requestWithdrawal(amount, payoutMethod, payoutDetails, null);
        log.info("Liquidity extraction requested into stream protocol: {} VND via {}", amount, payoutMethod);
    }

    public void setOnReturnAction(Runnable action) {
        this.onReturnAction = action;
    }

    /**
     * Severs active property event hook dependencies to guarantee proper memory reference collection scopes.
     */
    public void dispose() {
        if (requireTotpListener != null) {
            AuctionEventBus.removeListener(ClientPaymentHandler.REQUIRE_TOTP_PAYMENT, requireTotpListener);
        }
        if (invalidTotpListener != null) {
            AuctionEventBus.removeListener(ClientPaymentHandler.INVALID_TOTP, invalidTotpListener);
        }
    }

    @FXML
    private void handleReturn() {
        if (onReturnAction != null) {
            onReturnAction.run();
        }
    }

    @FXML
    private void handleDeposit() {
        long amount = parseDepositAmount();
        if (amount <= 0) return;

        AnimateEffect.pauseNode(btnDeposit, 2);
        sendDepositRequest(amount, null);
    }

    @FXML
    private void addQuickAmount(javafx.event.ActionEvent event) {
        Button btn = (Button) event.getSource();
        String text = btn.getText()
                .replace("+", "")
                .replace("k", "000")
                .replace("M", "000000");
        txtDepositAmount.setText(text);
    }

    private void setupTransactionTable() {
        colDate.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        tableTransactions.setItems(transactionData);
    }

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

        AuctionEventBus.addListener(ClientPaymentHandler.WITHDRAW_REQUEST_SUCCESS, event ->
                Platform.runLater(() -> {
                    AlertUtils.showInfo("Yêu cầu đã gửi", "Yêu cầu rút tiền đang chờ Admin duyệt.");
                    txtWithdrawAmount.clear();
                    txtPayoutDetails.clear();
                    WalletService.fetchWalletHistory();
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

    @SuppressWarnings("unchecked")
    private void handleWalletDataReceived(NetworkMessage response) {
        Map<String, Object> map = (Map<String, Object>) response.getData();
        long balance = Long.parseLong(map.get("balance").toString());
        long lockedBalance = Long.parseLong(map.get("lockedBalance").toString());
        List<Map<String, Object>> txs = (List<Map<String, Object>>) map.get("transactions");

        List<WalletTransaction> newTransactions = txs.stream()
                .map(this::mapToTransaction)
                .toList();

        Platform.runLater(() -> {
            updateBalanceDisplay(balance, lockedBalance);
            transactionData.setAll(newTransactions);
        });
    }

    private WalletTransaction mapToTransaction(Map<String, Object> map) {
        WalletTransaction tx = new WalletTransaction(
                map.get("id").toString(),
                "",
                ((Number) map.get("amount")).longValue(),
                map.get("description").toString()
        );
        tx.setCreatedAt(LocalDateTime.parse(map.get("createdAt").toString(), DateTimeFormatter.ISO_DATE_TIME));
        return tx;
    }

    private void updateBalanceDisplay(long balance, long frozenBalance) {
        currentBalance = balance;
        currentFrozenBalance = frozenBalance;
        lblTotalBalance.setText(String.format("%,d VND", currentBalance));
        lblFrozenBalance.setText(String.format("%,d VND", currentFrozenBalance));
    }

    private long parseDepositAmount() {
        String input = txtDepositAmount.getText().trim();
        try {
            long amount = Long.parseLong(input);
            if (amount <= 0) throw new NumberFormatException();
            return amount;
        } catch (NumberFormatException e) {
            AlertUtils.showError("Input Error", "Please enter a valid deposit amount.");
            return -1;
        }
    }

    private void sendDepositRequest(long amount, String totpCode) {
        pendingDepositAmount = amount;

        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", amount);
        if (totpCode != null && !totpCode.isBlank()) {
            payload.put("totpCode", totpCode);
        }

        NetworkService.sendMessage("CREATE_DEPOSIT", payload);
    }

    @SuppressWarnings("unchecked")
    private void onRequireTotpPayment(Object eventData) {
        Platform.runLater(() -> {
            long serverAmount = resolveAmountFromEvent(eventData);
            String totpCode = showTotpChallengeDialog(serverAmount);

            NetworkMessage msg = (NetworkMessage) eventData;
            Map<String, Object> data = (Map<String, Object>) msg.getData();
            if (data.containsKey("payoutMethod")) {
                String payoutMethod = (String) data.get("payoutMethod");
                String payoutDetails = (String) data.get("payoutDetails");
                Map<String, Object> payload = new HashMap<>();
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

    private void onInvalidTotp(Object eventData) {
        Platform.runLater(() -> {
            AlertUtils.showError(
                    "Invalid TOTP Code",
                    "The 6-digit code you entered is incorrect or has expired.\nPlease open Google Authenticator and enter the current code."
            );
            btnDeposit.setDisable(false);
        });
    }

    @SuppressWarnings("unchecked")
    private long resolveAmountFromEvent(Object eventData) {
        try {
            NetworkMessage msg = (NetworkMessage) eventData;
            Map<String, Object> data = (Map<String, Object>) msg.getData();
            if (data.containsKey("amount")) {
                return Long.parseLong(data.get("amount").toString());
            }
        } catch (Exception e) {
            log.warn("Payload telemetry conversion failed, utilizing memory reference limits: {}", e.getMessage());
        }
        return pendingDepositAmount;
    }

    private String showTotpChallengeDialog(long amount) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Security Verification");
        dialog.setHeaderText(String.format("🔐 Confirm transaction of %,d VND", amount));

        Label info = new Label("Your account has TOTP protection enabled for transactions.\nPlease open Google Authenticator and enter the current 6-digit code.");
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