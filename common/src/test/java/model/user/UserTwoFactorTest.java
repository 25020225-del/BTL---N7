package model.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("User — 2FA State Machine Tests")
class UserTwoFactorTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("U-1", "testuser", "pass", "Test User", "BUYER");
    }

    // ── 1. Trạng thái mặc định ────────────────────────────────────────

    @Test
    @DisplayName("Mặc định: twoFactorStatus = DISABLED, 2 cờ = false")
    void defaultState_shouldBeDisabledAndFlagsOff() {
        assertFalse(user.is2FAEnabled());
        assertFalse(user.isTotpLoginEnabled());
        assertFalse(user.isTotpPaymentEnabled());
        assertEquals(User.TwoFactorStatus.DISABLED, user.getTwoFactorStatus());
    }

    // ── 2. Chuyển sang ENABLED ────────────────────────────────────────

    @Test
    @DisplayName("Set ENABLED: is2FAEnabled() = true")
    void setEnabled_shouldMakeIs2FAEnabledTrue() {
        user.setTwoFactorStatus(User.TwoFactorStatus.ENABLED);
        assertTrue(user.is2FAEnabled());
    }

    // ── 3. Bất biến: DISABLE reset 2 cờ ─────────────────────────────

    @Test
    @DisplayName("Set DISABLED sau ENABLED: 2 cờ bị reset về false")
    void setDisabled_shouldResetBothFlags() {
        // Dùng Raw setter để bypass guard (giả lập đọc từ DB)
        user.setTwoFactorStatus(User.TwoFactorStatus.ENABLED);
        user.setTotpLoginEnabledRaw(true);
        user.setTotpPaymentEnabledRaw(true);

        // Verify flags are set
        assertTrue(user.isTotpLoginEnabled());
        assertTrue(user.isTotpPaymentEnabled());

        // Now disable
        user.setTwoFactorStatus(User.TwoFactorStatus.DISABLED);

        assertFalse(user.isTotpLoginEnabled(), "LoginEnabled phải bị reset khi DISABLED");
        assertFalse(user.isTotpPaymentEnabled(), "PaymentEnabled phải bị reset khi DISABLED");
    }

    @Test
    @DisplayName("Set PENDING sau ENABLED: 2 cờ cũng bị reset về false")
    void setPending_shouldAlsoResetFlags() {
        user.setTwoFactorStatus(User.TwoFactorStatus.ENABLED);
        user.setTotpLoginEnabledRaw(true);
        user.setTotpPaymentEnabledRaw(true);

        user.setTwoFactorStatus(User.TwoFactorStatus.PENDING);

        assertFalse(user.isTotpLoginEnabled());
        assertFalse(user.isTotpPaymentEnabled());
    }

    // ── 4. Guard của setTotpLoginEnabled / setTotpPaymentEnabled ─────

    @Test
    @DisplayName("setTotpLoginEnabled(true) khi chưa có totpSecret → IllegalStateException")
    void setTotpLoginEnabled_withoutSecret_shouldThrow() {
        // totpSecret vẫn null (chưa set)
        assertThrows(IllegalStateException.class,
                () -> user.setTotpLoginEnabled(true),
                "Phải throw khi totpSecret chưa được xác nhận");
    }

    @Test
    @DisplayName("setTotpPaymentEnabled(true) khi chưa có totpSecret → IllegalStateException")
    void setTotpPaymentEnabled_withoutSecret_shouldThrow() {
        assertThrows(IllegalStateException.class,
                () -> user.setTotpPaymentEnabled(true));
    }

    @Test
    @DisplayName("setTotpLoginEnabled(false) không cần totpSecret — không throw")
    void setTotpLoginEnabled_false_shouldNotThrow() {
        assertDoesNotThrow(() -> user.setTotpLoginEnabled(false));
    }

    // ── 5. Raw setters bypass guard (dùng trong DAO) ─────────────────

    @Test
    @DisplayName("setTotpLoginEnabledRaw() bypass guard, set thẳng giá trị")
    void rawSetter_shouldBypassGuard() {
        // Không set totpSecret, không set ENABLED — nhưng Raw không throw
        assertDoesNotThrow(() -> user.setTotpLoginEnabledRaw(true));
        assertTrue(user.isTotpLoginEnabled());

        assertDoesNotThrow(() -> user.setTotpPaymentEnabledRaw(true));
        assertTrue(user.isTotpPaymentEnabled());
    }

    // ── 6. set2FAEnabled (deprecated backward-compat) ────────────────

    @Test
    @DisplayName("set2FAEnabled(true) tương đương setTwoFactorStatus(ENABLED)")
    @SuppressWarnings("deprecation")
    void set2FAEnabled_true_setsStatusToEnabled() {
        user.set2FAEnabled(true);
        assertEquals(User.TwoFactorStatus.ENABLED, user.getTwoFactorStatus());
        assertTrue(user.is2FAEnabled());
    }

    @Test
    @DisplayName("set2FAEnabled(false) tương đương setTwoFactorStatus(DISABLED)")
    @SuppressWarnings("deprecation")
    void set2FAEnabled_false_setsStatusToDisabled() {
        user.setTwoFactorStatus(User.TwoFactorStatus.ENABLED);
        user.set2FAEnabled(false);
        assertEquals(User.TwoFactorStatus.DISABLED, user.getTwoFactorStatus());
        assertFalse(user.is2FAEnabled());
    }

    // ── 7. setTwoFactorStatus(null) → không throw, fallback DISABLED ─

    @Test
    @DisplayName("setTwoFactorStatus(null) → DISABLED (không NullPointerException)")
    void setStatus_null_shouldFallbackToDisabled() {
        user.setTwoFactorStatus(User.TwoFactorStatus.ENABLED);
        assertDoesNotThrow(() -> user.setTwoFactorStatus(null));
        assertEquals(User.TwoFactorStatus.DISABLED, user.getTwoFactorStatus());
    }
}