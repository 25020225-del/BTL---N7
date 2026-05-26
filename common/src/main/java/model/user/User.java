package model.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import model.base.Entity;

/**
 * Domain model representing a unified user aggregate within the auction ecosystem.
 * Encapsulates client identity attributes and enforces data invariants for multi-factor security preferences.
 */
public class User extends Entity {

    public enum TwoFactorStatus {
        DISABLED,
        PENDING,
        ENABLED
    }

    private String userName;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String name;
    private String role;
    private TwoFactorStatus twoFactorStatus = TwoFactorStatus.DISABLED;

    @JsonIgnore
    private String totpSecret;

    @JsonIgnore
    private String tempSecretKey;

    private boolean isGood;
    private boolean isTotpLoginEnabled = false;
    private boolean isTotpPaymentEnabled = false;

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
     * Checks enforcement block configurations assigned to this profile context.
     * Evaluates as the system single source of truth for immediate access revocation.
     *
     * @return true if account role parameters match the blocked state specification
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

    public TwoFactorStatus getTwoFactorStatus() {
        return this.twoFactorStatus;
    }

    /**
     * Modifies the authoritative two-factor state tracking properties.
     * Enforces downstream resets on granular preference flags if state changes to non-enabled bounds.
     *
     * @param status target lifecycle configuration state to assign
     */
    public void setTwoFactorStatus(TwoFactorStatus status) {
        this.twoFactorStatus = (status != null) ? status : TwoFactorStatus.DISABLED;
        if (this.twoFactorStatus != TwoFactorStatus.ENABLED) {
            this.isTotpLoginEnabled = false;
            this.isTotpPaymentEnabled = false;
        }
    }

    public boolean is2FAEnabled() {
        return twoFactorStatus == TwoFactorStatus.ENABLED;
    }

    /**
     * @deprecated Prefer direct updates via {@link #setTwoFactorStatus(TwoFactorStatus)}
     */

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

    public boolean isTotpLoginEnabled() {
        return isTotpLoginEnabled;
    }

    /**
     * Configures the verification mandate checkpoint during initial access authorization processing.
     *
     * @param enabled activation toggle preference flag
     * @throws IllegalStateException if verified secret key data is missing from current context
     */
    public void setTotpLoginEnabled(boolean enabled) {
        if (enabled && (totpSecret == null || totpSecret.isBlank())) {
            throw new IllegalStateException("isTotpLoginEnabled chỉ được set true khi totpSecret đã được xác nhận (ENABLED).");
        }
        this.isTotpLoginEnabled = enabled;
    }

    public boolean isTotpPaymentEnabled() {
        return isTotpPaymentEnabled;
    }

    /**
     * Configures the verification mandate checkpoint during accounting and asset locking mutations.
     *
     * @param enabled activation toggle preference flag
     * @throws IllegalStateException if verified secret key data is missing from current context
     */
    public void setTotpPaymentEnabled(boolean enabled) {
        if (enabled && (totpSecret == null || totpSecret.isBlank())) {
            throw new IllegalStateException("isTotpPaymentEnabled chỉ được set true khi totpSecret đã được xác nhận (ENABLED).");
        }
        this.isTotpPaymentEnabled = enabled;
    }

    public void setTotpLoginEnabledRaw(boolean v) {
        this.isTotpLoginEnabled = v;
    }

    public void setTotpPaymentEnabledRaw(boolean v) {
        this.isTotpPaymentEnabled = v;
    }

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