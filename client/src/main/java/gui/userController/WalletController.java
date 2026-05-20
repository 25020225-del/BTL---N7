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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import model.finance.WalletTransaction;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * WalletController acts as both a Controller and a Custom Node (VBox).
 */
public class WalletController extends VBox {
    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    private Runnable onReturnAction;


    @FXML
    private Label lblTotalBalance;
    @FXML
    private Label lblFrozenBalance;
    @FXML
    private TextField txtDepositAmount;

    @FXML
    private TableView<WalletTransaction> tableTransactions;
    @FXML
    private TableColumn<WalletTransaction, String> colId;
    @FXML
    private TableColumn<WalletTransaction, String> colDate;
    @FXML
    private TableColumn<WalletTransaction, Long> colAmount;
    @FXML
    private TableColumn<WalletTransaction, String> colDescription;

    @FXML
    private Button btnDeposit;
    @FXML
    private Button btnDepositVietQR;

    private long currentBalance = 0L;
    private long currentFrozenBalance = 0L;
    private ObservableList<WalletTransaction> transactionData = FXCollections.observableArrayList();
    // ── NEW: Lưu amount đang chờ TOTP để retry ───────────────────────
    /**
     * Lưu amount đang chờ xác thực để dùng khi retry.
     */
    private double pendingDepositAmount = 0.0;

    // ── EventBus listener để remove khi dispose ─────────────────────
    private java.beans.PropertyChangeListener requireTotpListener;
    private java.beans.PropertyChangeListener invalidTotpListener;

    public WalletController() {
        // Load FXML as a Custom Control
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/gui/WalletView.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load WalletView.fxml", e);
        }
    }

    @FXML
    public void initialize() {
        // Initialize table columns
        colDate.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));

        AuctionEventBus.addListener("FETCH_WALLET_SUCCESS", event -> {
            NetworkMessage response = (NetworkMessage) event.getNewValue();
            Map<String, Object> map = (Map<String, Object>) response.getData();
            long balance = Long.parseLong(map.get("balance").toString());
            long lockedBalance = Long.parseLong(map.get("lockedBalance").toString());
            List<Map<String, Object>> transactions = (List<Map<String, Object>>) map.get("transactions");
            transactionData.clear();
            transactions.forEach(transaction -> {
                WalletTransaction walletTransaction = new WalletTransaction(
                        transaction.get("id").toString(),
                        "",
                        ((Number) transaction.get("amount")).longValue(),
                        transaction.get("description").toString()
                );
                walletTransaction.setCreatedAt(LocalDateTime.parse(transaction.get("createdAt").toString(), DateTimeFormatter.ISO_DATE_TIME));
                transactionData.add(walletTransaction);
            });
            Platform.runLater(() -> {
                setWalletBalance(balance);
                setWalletLockedBalance(lockedBalance);
                tableTransactions.setItems(transactionData);
            });
        });

        AuctionEventBus.addListener("VIETQR_CREATED", event -> {
            Platform.runLater(() -> {
                @SuppressWarnings("unchecked")
                Map<String, String> data = (Map<String, String>) event.getNewValue();
                String qrString = data.get("qrString");
                String orderId = data.get("orderId");

                showVietQRDialog(qrString, orderId);
            });
        });

        requireTotpListener = event -> onRequireTotpPayment(event.getNewValue());
        invalidTotpListener = event -> onInvalidTotp(event.getNewValue());

        AuctionEventBus.addListener(
                ClientPaymentHandler.REQUIRE_TOTP_PAYMENT, requireTotpListener);
        AuctionEventBus.addListener(
                ClientPaymentHandler.INVALID_TOTP, invalidTotpListener);

        WalletService.fetchWalletHistory();
    }

    public void setOnReturnAction(Runnable action) {
        this.onReturnAction = action;
    }

    public void setWalletBalance(long balance) {
        currentBalance = balance;
        lblTotalBalance.setText(String.valueOf(currentBalance) + " N VND");
    }

    public void setWalletLockedBalance(long lockedBalance) {
        currentFrozenBalance = lockedBalance;
        lblFrozenBalance.setText(String.valueOf(currentFrozenBalance) + " N VND");
    }

    @FXML
    private void handleReturn() {
        if (onReturnAction != null) {
            onReturnAction.run();
        }
    }

    @FXML
    private void handleDeposit() {
        String input = txtDepositAmount.getText().trim();
        double amount;
        try {
            amount = Double.parseDouble(input);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Lỗi", "Vui lòng nhập số tiền hợp lệ!");
            return;
        }
        AnimateEffect.pauseNode(btnDeposit, 2);
        sendDepositRequest(amount, null);
    }

    @FXML
    private void handleVietQRDeposit() {
        String input = txtDepositAmount.getText().trim();
        double amount;
        try {
            amount = Double.parseDouble(input);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Error", "Please enter a valid amount.");
            return;
        }

        // Disable button for 2 secs to avoid spamming
        gui.process.AnimateEffect.pauseNode(btnDepositVietQR, 2);

        java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
        payload.put("amount", (long) amount);
        client.network.NetworkService.sendMessage("CREATE_VIETQR_DEPOSIT", payload);
    }

    /**
     * Gửi request nạp tiền lên server.
     *
     * @param amount   Số tiền nạp (VND).
     * @param totpCode Mã TOTP 6 số (null nếu chưa có).
     */
    private void sendDepositRequest(double amount, String totpCode) {
        pendingDepositAmount = amount; // Lưu lại để retry nếu bị TOTP challenge

        // Format mới: Map với amount và totpCode tùy chọn
        java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
        payload.put("amount", (long) amount);
        if (totpCode != null && !totpCode.isBlank()) {
            payload.put("totpCode", totpCode);
        }

        NetworkService.sendMessage("CREATE_DEPOSIT", payload);
    }

    // ── NEW: Xử lý REQUIRE_TOTP_PAYMENT ─────────────────────────────

    /**
     * Được gọi khi server yêu cầu TOTP trước khi nạp tiền.
     * Hiển thị dialog nhập mã 6 số và retry với mã đó.
     */
    @SuppressWarnings("unchecked")
    private void onRequireTotpPayment(Object eventData) {
        Platform.runLater(() -> {
            // Server echo lại amount; dùng pendingDepositAmount làm fallback
            long serverAmount = pendingDepositAmount > 0
                    ? (long) pendingDepositAmount
                    : 0L;

            try {
                NetworkMessage msg = (NetworkMessage) eventData;
                Map<String, Object> data = (Map<String, Object>) msg.getData();
                if (data.containsKey("amount")) {
                    serverAmount = Long.parseLong(data.get("amount").toString());
                }
            } catch (Exception ignored) {
            }

            String totpCode = showTotpChallengeDialog(serverAmount);
            if (totpCode != null) {
                // User đã nhập mã → retry với TOTP
                retryDepositWithTotp(serverAmount, totpCode);
            } else {
                // User hủy dialog → không làm gì thêm
                log.info("User cancelled TOTP challenge for deposit.");
                btnDeposit.setDisable(false); // Cho phép thử lại sau
            }
        });
    }

    /**
     * Được gọi khi server báo mã TOTP không hợp lệ.
     * Hiển thị thông báo và cho phép thử lại.
     */
    private void onInvalidTotp(Object eventData) {
        Platform.runLater(() -> {
            AlertHelper.showAlert(
                    Alert.AlertType.ERROR,
                    "Mã TOTP không hợp lệ",
                    "Mã 6 số bạn nhập không đúng hoặc đã hết hạn.\n"
                            + "Vui lòng mở Google Authenticator và nhập mã mới."
            );
            // Không retry tự động; để user bấm nạp tiền lại nếu muốn
            btnDeposit.setDisable(false);
        });
    }

    /**
     * Hiển thị dialog yêu cầu nhập mã TOTP.
     * Tái sử dụng UI pattern đơn giản (TextField 6 số).
     *
     * @param amount Số tiền đang chờ xác thực (hiển thị để user biết context).
     * @return Chuỗi mã 6 số đã nhập, hoặc {@code null} nếu user hủy.
     */
    private String showTotpChallengeDialog(long amount) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Xác thực bảo mật");
        dialog.setHeaderText("🔐 Xác nhận giao dịch " + String.format("%,d", amount) + " VND");

        Label info = new Label(
                "Tài khoản của bạn đã bật bảo vệ TOTP cho giao dịch.\n"
                        + "Hãy mở Google Authenticator và nhập mã 6 số hiện tại.");
        info.setWrapText(true);

        TextField otpField = new TextField();
        otpField.setPromptText("Nhập mã 6 số...");
        otpField.textProperty().addListener((obs, oldVal, newVal) -> {
            // Chỉ cho nhập số, tối đa 6 ký tự
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

    /**
     * Gửi lại lệnh CREATE_DEPOSIT kèm mã TOTP.
     */
    private void retryDepositWithTotp(long amount, String totpCode) {
        log.info("Retrying deposit with TOTP code for amount {}", amount);
        sendDepositRequest(amount, totpCode);
    }

    /**
     * Gọi khi WalletController bị đóng để giải phóng EventBus listeners.
     * Thêm vào method dispose() hiện có hoặc tạo mới nếu chưa có.
     */
    public void dispose() {
        if (requireTotpListener != null) {
            AuctionEventBus.removeListener(
                    ClientPaymentHandler.REQUIRE_TOTP_PAYMENT, requireTotpListener);
        }
        if (invalidTotpListener != null) {
            AuctionEventBus.removeListener(
                    ClientPaymentHandler.INVALID_TOTP, invalidTotpListener);
        }
    }


    @FXML
    private void addQuickAmount(javafx.event.ActionEvent event) {
        Button btn = (Button) event.getSource();
        String text = btn.getText().replace("+", "").replace("k", "000").replace("M", "000000");
        txtDepositAmount.setText(text);
    }

    private void showVietQRDialog(String qrString, String orderId) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Pay via VietQR");
        dialog.setHeaderText(null);

        VBox mainContainer = new VBox(25);
        mainContainer.setAlignment(javafx.geometry.Pos.CENTER);
        mainContainer.setStyle("-fx-background-color: white; -fx-padding: 40 50;");

        Label lblTitle = new Label("Scan QR Code to pay");
        lblTitle.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #0a2540;");

        VBox qrBox = new VBox(15);
        qrBox.setAlignment(javafx.geometry.Pos.CENTER);
        qrBox.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 30; -fx-background-radius: 12;");

        javafx.scene.layout.StackPane qrStack = new javafx.scene.layout.StackPane();
        qrStack.setMaxSize(270, 270);
        VBox.setMargin(qrStack, new javafx.geometry.Insets(10));

        javafx.scene.image.Image qrImage = gui.process.QRCodeHelper.generateQRCodeImage(qrString, 250, 250);
        javafx.scene.image.ImageView qrImageView = new javafx.scene.image.ImageView(qrImage);

        javafx.scene.image.Image logoImage = new javafx.scene.image.Image(
                getClass().getResourceAsStream("/gui/images/VietQRLogo.png")
        );
        javafx.scene.image.ImageView logoView = new javafx.scene.image.ImageView(logoImage);
        logoView.setFitWidth(30);
        logoView.setFitHeight(30);
        logoView.setPreserveRatio(true);

        javafx.scene.shape.Circle logoBg = new javafx.scene.shape.Circle(24, javafx.scene.paint.Color.WHITE);
        javafx.scene.layout.StackPane centerLogo = new javafx.scene.layout.StackPane(logoBg, logoView);
        javafx.scene.layout.StackPane.setAlignment(centerLogo, javafx.geometry.Pos.CENTER);

        // 3. Các góc trang trí
        String cornerStyle = "-fx-border-color: #3665f3; ";
        int cornerSize = 25;
        javafx.scene.layout.Region tl = new javafx.scene.layout.Region(); tl.setMaxSize(cornerSize, cornerSize); tl.setStyle(cornerStyle + "-fx-border-width: 3 0 0 3;"); javafx.scene.layout.StackPane.setAlignment(tl, javafx.geometry.Pos.TOP_LEFT);
        javafx.scene.layout.Region tr = new javafx.scene.layout.Region(); tr.setMaxSize(cornerSize, cornerSize); tr.setStyle(cornerStyle + "-fx-border-width: 3 3 0 0;"); javafx.scene.layout.StackPane.setAlignment(tr, javafx.geometry.Pos.TOP_RIGHT);
        javafx.scene.layout.Region bl = new javafx.scene.layout.Region(); bl.setMaxSize(cornerSize, cornerSize); bl.setStyle(cornerStyle + "-fx-border-width: 0 0 3 3;"); javafx.scene.layout.StackPane.setAlignment(bl, javafx.geometry.Pos.BOTTOM_LEFT);
        javafx.scene.layout.Region br = new javafx.scene.layout.Region(); br.setMaxSize(cornerSize, cornerSize); br.setStyle(cornerStyle + "-fx-border-width: 0 3 3 0;"); javafx.scene.layout.StackPane.setAlignment(br, javafx.geometry.Pos.BOTTOM_RIGHT);

        qrStack.getChildren().addAll(qrImageView, centerLogo, tl, tr, bl, br);

        // 4. Thông tin tài khoản và mã đơn
        Label lblBank = new Label("Ngân hàng TMCP Quân Đội (MBBank)");
        lblBank.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #6c757d;");

        Label lblName = new Label("NGUYỄN QUANG MẠNH");
        lblName.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #0a2540;");

        Label lblAccount = new Label("0815567462");
        lblAccount.setStyle("-fx-font-size: 14px; -fx-text-fill: #555555;");

        HBox memoBox = new HBox(8);
        memoBox.setAlignment(javafx.geometry.Pos.CENTER);
        Label lblMemo = new Label(orderId);
        lblMemo.setStyle("-fx-font-size: 16px; -fx-text-fill: #6c757d;");

        Button btnCopy = new Button();
        org.kordamp.ikonli.javafx.FontIcon copyIcon = new org.kordamp.ikonli.javafx.FontIcon("mdi2c-content-copy");
        copyIcon.setIconColor(javafx.scene.paint.Color.web("#3665f3"));
        copyIcon.setIconSize(18);
        btnCopy.setGraphic(copyIcon);
        btnCopy.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 0;");

        btnCopy.setOnAction(e -> {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(orderId);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            copyIcon.setIconLiteral("mdi2c-check");
            copyIcon.setIconColor(javafx.scene.paint.Color.GREEN);
            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
            pause.setOnFinished(event -> { copyIcon.setIconLiteral("mdi2c-content-copy"); copyIcon.setIconColor(javafx.scene.paint.Color.web("#3665f3")); });
            pause.play();
        });

        memoBox.getChildren().addAll(lblMemo, btnCopy);
        qrBox.getChildren().addAll(qrStack, lblBank, lblName, lblAccount, memoBox);

        // 5. Footer
        Label lblInstruction = new Label("Open Internet Banking/Wallet App supporting VietQR to continue");
        lblInstruction.setStyle("-fx-font-size: 16px; -fx-text-fill: #0a2540; -fx-font-weight: bold;");

        mainContainer.getChildren().addAll(lblTitle, qrBox, lblInstruction);
        dialog.getDialogPane().setContent(mainContainer);
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dialog.show();
    }
}