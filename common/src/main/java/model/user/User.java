package model.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import model.base.Entity;

/**
 * Represents a unified user within the auction system.
 *
 * <p><b>2FA Lifecycle (3-State Machine):</b></p>
 * <pre>
 *   DISABLED ──[REQUEST_SETUP_2FA]──► PENDING ──[VERIFY_2FA_SETUP / CANCEL / Login]──► DISABLED
 *                                              └──[VERIFY_2FA_SETUP OK]──────────────► ENABLED
 *   ENABLED  ──[DISABLE_2FA]────────────────────────────────────────────────────────► DISABLED
 *
 *   ENABLED + isTotpLoginEnabled   = true  → TOTP required at Login
 *   ENABLED + isTotpPaymentEnabled = true  → TOTP required at Payment/Deposit
 * </pre>
 *
 * <p><b>QUAN TRỌNG về logic 2 cờ:</b> Cả hai cờ CHỈ được phép set thành {@code true}
 * khi {@code twoFactorStatus == ENABLED}. Khi gọi {@link #setTwoFactorStatus}
 * với trạng thái khác ENABLED, cả hai cờ sẽ bị tự động reset về {@code false}.</p>
 */
public class User extends Entity {

    // ─────────────────────────────────────────────────────────────────
    // INNER ENUM — TwoFactorStatus  (GIỮ NGUYÊN)
    // ─────────────────────────────────────────────────────────────────

    public enum TwoFactorStatus {
        DISABLED,
        PENDING,
        ENABLED
    }

    // ─────────────────────────────────────────────────────────────────
    // FIELDS
    // ─────────────────────────────────────────────────────────────────

    private String userName;
    private String password;
    private String name;
    private String role;

    /**
     * The authoritative 3-state 2FA lifecycle status.
     */
    private TwoFactorStatus twoFactorStatus = TwoFactorStatus.DISABLED;

    /**
     * The confirmed TOTP secret — always @JsonIgnore; never sent to client.
     * Active only when {@code twoFactorStatus == ENABLED}.
     */
    @JsonIgnore
    private String totpSecret;

    /**
     * A provisional TOTP secret written to the DB when status is {@code PENDING}.
     * Always {@code @JsonIgnore}.
     */
    @JsonIgnore
    private String tempSecretKey;

    /**
     * Trusted-user flag.
     */
    private boolean isGood;

    // ── NEW FIELDS ────────────────────────────────────────────────────

    /**
     * Khi {@code true}: TOTP được yêu cầu tại bước Đăng nhập.
     * Chỉ có hiệu lực khi {@code twoFactorStatus == ENABLED}.
     *
     * <p>Được serialize về client (không @JsonIgnore) để SettingsController
     * hiển thị đúng trạng thái checkbox.</p>
     */
    private boolean isTotpLoginEnabled = false;

    /**
     * Khi {@code true}: TOTP được yêu cầu trước khi xử lý Nạp tiền / Thanh toán.
     * Chỉ có hiệu lực khi {@code twoFactorStatus == ENABLED}.
     */
    private boolean isTotpPaymentEnabled = false;

    // ─────────────────────────────────────────────────────────────────
    // CONSTRUCTORS  (GIỮ NGUYÊN)
    // ─────────────────────────────────────────────────────────────────

    public User() {
        super();
    }

    public User(String id, String userName, String password, String name, String role) {
        super(id);
        this.userName = userName;
        this.password = password;
        this.name = name;
        this.role = role;
    }

    public User(String id, String userName, String password, String name) {
        super(id);
        this.userName = userName;
        this.password = password;
        this.name = name;
    }

    // ─────────────────────────────────────────────────────────────────
    // GETTERS & SETTERS — core fields  (GIỮ NGUYÊN)
    // ─────────────────────────────────────────────────────────────────

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    /**
     * Kiểm tra xem tài khoản có đang bị Admin khóa hay không.
     *
     * <p>Phương thức này là nguồn sự thật duy nhất (Single Source of Truth)
     * cho mọi logic chặn quyền trong hệ thống. Quy ước: UserDAO.mapUser()
     * đã thiết lập role = "BLOCKED" khi cột is_blocked = 1 trong DB.</p>
     *
     * @return {@code true} nếu role == "BLOCKED" (không phân biệt hoa thường).
     * *
     */
    public boolean isBlocked() {
        return "BLOCKED".equalsIgnoreCase(this.role);
    }

    public boolean isGood() {
        return isGood;
    }

    public void setGood(boolean good) {
        this.isGood = good;
    }

    /**
     * Lấy trạng thái xác thực 2 lớp (2FA) hiện tại của người dùng.
     *
     * @return Đối tượng TwoFactorStatus (ENABLED, PENDING, hoặc DISABLED).
     */
    public TwoFactorStatus getTwoFactorStatus() {
        return this.twoFactorStatus;
    }

    /**
     * Sets the 2FA lifecycle status.
     * QUAN TRỌNG: Nếu status KHÔNG phải ENABLED, tự động reset cả 2 cờ về false
     * để đảm bảo tính nhất quán.
     */
    public void setTwoFactorStatus(TwoFactorStatus status) {
        this.twoFactorStatus = (status != null) ? status : TwoFactorStatus.DISABLED;
        // Bất biến: 2 cờ chỉ được true khi ENABLED
        if (this.twoFactorStatus != TwoFactorStatus.ENABLED) {
            this.isTotpLoginEnabled = false;
            this.isTotpPaymentEnabled = false;
        }
    }

    /**
     * Backward-compatible: true only when fully ENABLED.
     * Dùng cho code cũ không liên quan đến login/payment phân tách.
     */
    public boolean is2FAEnabled() {
        return twoFactorStatus == TwoFactorStatus.ENABLED;
    }

    /**
     * @deprecated Prefer {@link #setTwoFactorStatus(TwoFactorStatus)}.
     */
    @Deprecated
    public void set2FAEnabled(boolean v) {
        setTwoFactorStatus(v ? TwoFactorStatus.ENABLED : TwoFactorStatus.DISABLED);
    }

    @JsonIgnore
    public String getTotpSecret() {
        return totpSecret;
    }

    @JsonIgnore
    public void setTotpSecret(String v) {
        this.totpSecret = v;
    }

    @JsonIgnore
    public String getTempSecretKey() {
        return tempSecretKey;
    }

    @JsonIgnore
    public void setTempSecretKey(String v) {
        this.tempSecretKey = v;
    }

    // ─────────────────────────────────────────────────────────────────
    // GETTERS & SETTERS — TOTP Granular Prefs  (NEW)
    // ─────────────────────────────────────────────────────────────────

    /**
     * True khi TOTP được yêu cầu tại bước Đăng nhập.
     */
    public boolean isTotpLoginEnabled() {
        return isTotpLoginEnabled;
    }

    /**
     * Set cờ Login TOTP.
     * Guard: chỉ cho phép set true nếu totpSecret đã được thiết lập (ENABLED state).
     */
    public void setTotpLoginEnabled(boolean enabled) {
        if (enabled && (totpSecret == null || totpSecret.isBlank())) {
            throw new IllegalStateException(
                    "isTotpLoginEnabled chỉ được set true khi totpSecret đã được xác nhận (ENABLED).");
        }
        this.isTotpLoginEnabled = enabled;
    }

    /**
     * True khi TOTP được yêu cầu trước Giao dịch tài chính.
     */
    public boolean isTotpPaymentEnabled() {
        return isTotpPaymentEnabled;
    }

    /**
     * Set cờ Payment TOTP.
     * Guard: chỉ cho phép set true nếu totpSecret đã được thiết lập (ENABLED state).
     */
    public void setTotpPaymentEnabled(boolean enabled) {
        if (enabled && (totpSecret == null || totpSecret.isBlank())) {
            throw new IllegalStateException(
                    "isTotpPaymentEnabled chỉ được set true khi totpSecret đã được xác nhận (ENABLED).");
        }
        this.isTotpPaymentEnabled = enabled;
    }

    /**
     * Internal setter dùng trong UserDAO.mapUser() và UserController —
     * không enforce guard vì DB là source of truth tại thời điểm đọc.
     */
    public void setTotpLoginEnabledRaw(boolean v) {
        this.isTotpLoginEnabled = v;
    }

    public void setTotpPaymentEnabledRaw(boolean v) {
        this.isTotpPaymentEnabled = v;
    }

    // ─────────────────────────────────────────────────────────────────
    // ENTITY INTERFACE
    // ─────────────────────────────────────────────────────────────────

    @Override
    public String getInfo() {
        String tag = this.isGood() ? "[TRUSTED] " : "";
        return tag + "ID: " + this.getId()
                + " | Username: " + userName
                + " | Name: " + name
                + " | Role: " + role
                + " | 2FA: " + twoFactorStatus
                + " | LoginOTP: " + isTotpLoginEnabled
                + " | PayOTP: " + isTotpPaymentEnabled;
    }
}