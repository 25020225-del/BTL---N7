package gui;

import client.handler.AuctionEventBus;
import client.network.NetworkService;
import com.fasterxml.jackson.databind.ObjectMapper;
import gui.process.AlertUtils;
import gui.process.QRCodeHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import model.user.User;
import model.user.User.TwoFactorStatus;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JacksonConfig;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Controller cho màn hình Settings — quản lý TOTP phân tách Login/Payment.
 *
 * <p><b>UI State Machine:</b></p>
 * <pre>
 *   twoFactorStatus != ENABLED  →  hiển thị [vboxSetupTotp]
 *   twoFactorStatus == ENABLED  →  hiển thị [vboxTotpPrefs]
 *                                    ├─ chkLoginEnabled   (checked = isTotpLoginEnabled)
 *                                    ├─ chkPaymentEnabled (checked = isTotpPaymentEnabled)
 *                                    └─ btnDisableTotp
 * </pre>
 */
public class SettingsController extends VBox {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);
    private final ObjectMapper mapper = JacksonConfig.mapper();

    // ── FXML — Section 1: Chưa setup ─────────────────────────────────
    @FXML
    private VBox vboxSetupTotp;
    @FXML
    private Button btnSetupTotp;

    // ── FXML — Section 2: Đã setup, quản lý prefs ───────────────────
    @FXML
    private VBox vboxTotpPrefs;
    @FXML
    private CheckBox chkLoginEnabled;
    @FXML
    private CheckBox chkPaymentEnabled;
    @FXML
    private Label lblPrefsStatus;
    @FXML
    private Button btnDisableTotp;

    // ── Dependencies ─────────────────────────────────────────────────
    private User currentUser;
    private Runnable onBackToMarketplace;
    private Runnable onSignOut;

    // ── EventBus listeners ───────────────────────────────────────────
    private java.beans.PropertyChangeListener setup2FAListener;
    private java.beans.PropertyChangeListener confirm2FAListener;
    private java.beans.PropertyChangeListener cancel2FAListener;
    private java.beans.PropertyChangeListener disable2FAListener;
    private java.beans.PropertyChangeListener updatePrefsListener; // NEW

    // ── Constructor ──────────────────────────────────────────────────

    public SettingsController(User user) {
        this.currentUser = user;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("SettingsView.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Không thể load SettingsView.fxml", e);
        }
    }

    public void setOnBackToMarketplace(Runnable r) {
        this.onBackToMarketplace = r;
    }

    public void setOnSignOut(Runnable r) {
        this.onSignOut = r;
    }

    // ── FXML initialize ──────────────────────────────────────────────

    @FXML
    public void initialize() {
        refreshTotpUI();
        registerEventListeners();
    }

    @FXML
    public void handleBackToMarketplace() {
        if (onBackToMarketplace != null) onBackToMarketplace.run();
    }

    @FXML
    public void handleSignOut() {
        if (onSignOut != null) onSignOut.run();
    }

    // ── FXML Actions ─────────────────────────────────────────────────

    /**
     * Nút "Thiết lập TOTP" — chỉ hiện khi chưa có secret.
     */
    @FXML
    public void handleSetupTotp() {
        btnSetupTotp.setDisable(true);
        NetworkService.sendMessage("REQUEST_SETUP_2FA", "");
    }

    /**
     * Gọi khi user tích/bỏ tích một trong 2 CheckBox.
     * Lấy trạng thái hiện tại của cả 2 checkbox và gửi lên server.
     */
    @FXML
    public void handleTotpPrefsChanged() {
        boolean loginEnabled = chkLoginEnabled.isSelected();
        boolean paymentEnabled = chkPaymentEnabled.isSelected();

        // Vô hiệu hóa tạm thời để tránh double-click
        chkLoginEnabled.setDisable(true);
        chkPaymentEnabled.setDisable(true);
        lblPrefsStatus.setText("⏳ Đang lưu...");

        NetworkService.sendMessage("UPDATE_TOTP_PREFS",
                Map.of(
                        "loginEnabled", loginEnabled,
                        "paymentEnabled", paymentEnabled
                ));
    }

    /**
     * Nút "Hủy hoàn toàn TOTP".
     */
    @FXML
    public void handleDisableTotp() {
        // Cảnh báo 2 bước (giống code gốc)
        Alert warn = new Alert(Alert.AlertType.CONFIRMATION);
        warn.setTitle("Hủy TOTP hoàn toàn");
        warn.setHeaderText("⚠ Xác nhận hủy bỏ xác thực 2 lớp");
        warn.setContentText(
                "Hành động này sẽ:\n"
                        + "  • Xóa toàn bộ secret TOTP đã thiết lập.\n"
                        + "  • Tắt yêu cầu OTP cho cả Đăng nhập và Giao dịch.\n\n"
                        + "Bạn có thể thiết lập lại TOTP bất kỳ lúc nào.\n"
                        + "Bạn có chắc chắn?");

        Optional<ButtonType> result = warn.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        // Xác nhận bằng mật khẩu (giống code gốc)
        Dialog<String> passDialog = new Dialog<>();
        passDialog.setTitle("Xác nhận hủy TOTP");
        passDialog.setHeaderText("Nhập mật khẩu để xác nhận");
        PasswordField passField = new PasswordField();
        passField.setPromptText("Mật khẩu hiện tại...");
        VBox content = new VBox(8, new Label("Mật khẩu:"), passField);
        passDialog.getDialogPane().setContent(content);
        passDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        passDialog.setResultConverter(btn -> btn == ButtonType.OK ? passField.getText() : null);

        Optional<String> passResult = passDialog.showAndWait();
        if (passResult.isPresent() && passResult.get() != null && !passResult.get().isBlank()) {
            btnDisableTotp.setDisable(true);
            NetworkService.sendMessage("DISABLE_2FA",
                    Map.of("password", passResult.get()));
        }
    }

    // ── EventBus Response Handlers ────────────────────────────────────

    /**
     * Server trả về QR code → hiển thị dialog quét QR.
     */
    @SuppressWarnings("unchecked")
    private void onSetup2FASuccess(Object eventData) {
        Platform.runLater(() -> {
            btnSetupTotp.setDisable(false);
            try {
                NetworkMessage msg = (NetworkMessage) eventData;
                Map<String, String> totpData = (Map<String, String>) msg.getData();
                showQRDialog(totpData.get("secretKey"), totpData.get("qrUrl"));
            } catch (Exception e) {
                log.error("Error parsing SETUP_2FA_SUCCESS: {}", e.getMessage());
                AlertUtils.showError(
                        "Lỗi", "Không thể tạo mã QR. Vui lòng thử lại.");
            }
        });
    }

    /**
     * User xác nhận OTP thành công → ENABLED; hiển thị section quản lý prefs.
     */
    private void onConfirm2FASuccess(Object eventData) {
        Platform.runLater(() -> {
            currentUser.setTwoFactorStatus(TwoFactorStatus.ENABLED);
            // Server sẽ gửi lại user object đã cập nhật, nhưng ta cập nhật local ngay
            refreshTotpUI();
            AlertUtils.showInfo(
                    "TOTP đã thiết lập ✅",
                    "Xác thực 2 lớp đã được cài đặt thành công!\n\n"
                            + "Hãy chọn bên dưới để quyết định khi nào cần nhập mã OTP.");
        });
    }

    /**
     * User hủy QR dialog → server reset PENDING → DISABLED.
     */
    private void onCancel2FASuccess(Object eventData) {
        Platform.runLater(() -> {
            currentUser.setTwoFactorStatus(TwoFactorStatus.DISABLED);
            refreshTotpUI();
        });
    }

    /**
     * Server confirm DISABLE_2FA_SUCCESS → quay về state ban đầu.
     */
    private void onDisable2FASuccess(Object eventData) {
        Platform.runLater(() -> {
            currentUser.setTwoFactorStatus(TwoFactorStatus.DISABLED);
            // setTwoFactorStatus(DISABLED) đã tự reset 2 cờ trong User.java
            refreshTotpUI();
            AlertUtils.showInfo(
                    "TOTP đã hủy", "Xác thực 2 lớp đã được gỡ bỏ hoàn toàn.");
        });
    }

    /**
     * Server confirm UPDATE_TOTP_PREFS_SUCCESS → cập nhật in-memory và UI.
     */
    @SuppressWarnings("unchecked")
    private void onUpdateTotpPrefsSuccess(Object eventData) {
        Platform.runLater(() -> {
            try {
                NetworkMessage msg = (NetworkMessage) eventData;
                Map<String, Object> data = (Map<String, Object>) msg.getData();
                boolean loginEnabled = Boolean.TRUE.equals(data.get("loginEnabled"));
                boolean paymentEnabled = Boolean.TRUE.equals(data.get("paymentEnabled"));

                // Cập nhật in-memory user (raw setter — secret đã tồn tại)
                currentUser.setTotpLoginEnabledRaw(loginEnabled);
                currentUser.setTotpPaymentEnabledRaw(paymentEnabled);

                // Đồng bộ lại checkbox (phòng trường hợp server trả về khác request)
                chkLoginEnabled.setSelected(loginEnabled);
                chkPaymentEnabled.setSelected(paymentEnabled);

                // Hiện thị trạng thái hiện tại
                lblPrefsStatus.setText(buildPrefsStatusText(loginEnabled, paymentEnabled));

            } catch (Exception e) {
                log.error("Error parsing UPDATE_TOTP_PREFS_SUCCESS: {}", e.getMessage());
                lblPrefsStatus.setText("❌ Lỗi cập nhật. Vui lòng thử lại.");
            } finally {
                chkLoginEnabled.setDisable(false);
                chkPaymentEnabled.setDisable(false);
            }
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Đồng bộ toàn bộ UI dựa trên trạng thái hiện tại của {@code currentUser}.
     * Gọi khi: initialize(), sau mỗi response từ server.
     */
    private void refreshTotpUI() {
        boolean isEnabled = currentUser.is2FAEnabled(); // twoFactorStatus == ENABLED

        // Toggle visibility của 2 section
        vboxSetupTotp.setVisible(!isEnabled);
        vboxSetupTotp.setManaged(!isEnabled);
        vboxTotpPrefs.setVisible(isEnabled);
        vboxTotpPrefs.setManaged(isEnabled);

        if (isEnabled) {
            // Đồng bộ checkbox với trạng thái in-memory của user
            chkLoginEnabled.setSelected(currentUser.isTotpLoginEnabled());
            chkPaymentEnabled.setSelected(currentUser.isTotpPaymentEnabled());
            chkLoginEnabled.setDisable(false);
            chkPaymentEnabled.setDisable(false);
            btnDisableTotp.setDisable(false);
            lblPrefsStatus.setText(
                    buildPrefsStatusText(currentUser.isTotpLoginEnabled(),
                            currentUser.isTotpPaymentEnabled()));
        }
    }

    /**
     * Tạo text mô tả trạng thái cờ hiện tại.
     */
    private String buildPrefsStatusText(boolean loginEnabled, boolean paymentEnabled) {
        if (!loginEnabled && !paymentEnabled) {
            return "ℹ️  TOTP đã thiết lập nhưng chưa áp dụng cho tình huống nào.";
        }
        StringBuilder sb = new StringBuilder("🛡 Đang bảo vệ: ");
        if (loginEnabled) sb.append("Đăng nhập ");
        if (paymentEnabled) sb.append("Giao dịch");
        return sb.toString().trim();
    }

    /**
     * Hiển thị QR dialog (GIỮ NGUYÊN logic gốc).
     * User nhập OTP → gửi VERIFY_2FA_SETUP / Cancel → gửi CANCEL_2FA_SETUP.
     */
    private void showQRDialog(String secretKey, String qrUrl) {
        Dialog<Integer> dialog = new Dialog<>();
        dialog.setTitle("Thiết lập xác thực 2 lớp (TOTP)");
        dialog.setHeaderText("Quét mã QR bằng Google Authenticator");

        VBox content = new VBox(12);
        Label instrLabel = new Label(
                "1. Mở Google Authenticator trên điện thoại.\n"
                        + "2. Quét mã QR bên dưới.\n"
                        + "3. Nhập mã 6 số vào ô xác nhận.\n\n"
                        + "Hoặc nhập thủ công secret key: " + secretKey);
        instrLabel.setWrapText(true);

        Image qrImage = QRCodeHelper.generateQRCodeImage(qrUrl, 220, 220);
        if (qrImage != null) {
            content.getChildren().addAll(instrLabel, new ImageView(qrImage));
        } else {
            content.getChildren().add(instrLabel);
        }

        TextField otpField = new TextField();
        otpField.setPromptText("Nhập mã 6 số từ ứng dụng...");
        content.getChildren().addAll(new Label("Mã xác nhận:"), otpField);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    return Integer.parseInt(otpField.getText().trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });

        Optional<Integer> result = dialog.showAndWait();
        if (result.isPresent() && result.get() != null) {
            NetworkService.sendMessage("VERIFY_2FA_SETUP", Map.of("code", result.get()));
        } else {
            log.info("User cancelled QR dialog. Sending CANCEL_2FA_SETUP.");
            NetworkService.sendMessage("CANCEL_2FA_SETUP", "");
        }
    }

    // ── EventBus registration ─────────────────────────────────────────

    private void registerEventListeners() {
        setup2FAListener = e -> onSetup2FASuccess(e.getNewValue());
        confirm2FAListener = e -> onConfirm2FASuccess(e.getNewValue());
        cancel2FAListener = e -> onCancel2FASuccess(e.getNewValue());
        disable2FAListener = e -> onDisable2FASuccess(e.getNewValue());
        updatePrefsListener = e -> onUpdateTotpPrefsSuccess(e.getNewValue()); // NEW

        AuctionEventBus.addListener("SETUP_2FA_SUCCESS", setup2FAListener);
        AuctionEventBus.addListener("CONFIRM_2FA_SUCCESS", confirm2FAListener);
        AuctionEventBus.addListener("CANCEL_2FA_SUCCESS", cancel2FAListener);
        AuctionEventBus.addListener("DISABLE_2FA_SUCCESS", disable2FAListener);
        AuctionEventBus.addListener("UPDATE_TOTP_PREFS_SUCCESS", updatePrefsListener); // NEW
    }

    /**
     * Gọi khi view bị ẩn/dispose để tránh memory leak.
     */
    public void dispose() {
        AuctionEventBus.removeListener("SETUP_2FA_SUCCESS", setup2FAListener);
        AuctionEventBus.removeListener("CONFIRM_2FA_SUCCESS", confirm2FAListener);
        AuctionEventBus.removeListener("CANCEL_2FA_SUCCESS", cancel2FAListener);
        AuctionEventBus.removeListener("DISABLE_2FA_SUCCESS", disable2FAListener);
        AuctionEventBus.removeListener("UPDATE_TOTP_PREFS_SUCCESS", updatePrefsListener); // NEW
    }
}