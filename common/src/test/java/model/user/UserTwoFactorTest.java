package model.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lifecycle state machine unit tests for user two-factor authentication (2FA).
 * Validates state transition invariants, strict permission guards, and legacy backward-compatibility pathways.
 */
@DisplayName("User — 2FA State Machine Tests")
class UserTwoFactorTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("U-1", "testuser", "pass", "Test User", "BUYER");
    }

    @Test
    @DisplayName("Mặc định: twoFactorStatus = DISABLED, 2 cờ = false")
    void defaultState_shouldBeDisabledAndFlagsOff() {
        assertFalse(user.is2FAEnabled());
        assertFalse(user.isTotpLoginEnabled());
        assertFalse(user.isTotpPaymentEnabled());
        assertEquals(User.TwoFactorStatus.DISABLED, user.getTwoFactorStatus());
    }

    @Test
    @DisplayName("Set ENABLED: is2FAEnabled() = true")
    void setEnabled_shouldMakeIs2FAEnabledTrue() {
        user.setTwoFactorStatus(User.TwoFactorStatus.ENABLED);
        assertTrue(user.is2FAEnabled());
    }

    @Test
    @DisplayName("Set DISABLED sau ENABLED: 2 cờ bị reset về false")
    void setDisabled_shouldResetBothFlags() {
        user.setTwoFactorStatus(User.TwoFactorStatus.ENABLED);
        user.setTotpLoginEnabledRaw(true);
        user.setTotpPaymentEnabledRaw(true);

        assertTrue(user.isTotpLoginEnabled());
        assertTrue(user.isTotpPaymentEnabled());

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

    @Test
    @DisplayName("setTotpLoginEnabled(true) khi chưa có totpSecret → IllegalStateException")
    void setTotpLoginEnabled_withoutSecret_shouldThrow() {
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

    @Test
    @DisplayName("setTotpLoginEnabledRaw() bypass guard, set thẳng giá trị")
    void rawSetter_shouldBypassGuard() {
        assertDoesNotThrow(() -> user.setTotpLoginEnabledRaw(true));
        assertTrue(user.isTotpLoginEnabled());

        assertDoesNotThrow(() -> user.setTotpPaymentEnabledRaw(true));
        assertTrue(user.isTotpPaymentEnabled());
    }

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

    @Test
    @DisplayName("setTwoFactorStatus(null) → DISABLED (không NullPointerException)")
    void setStatus_null_shouldFallbackToDisabled() {
        user.setTwoFactorStatus(User.TwoFactorStatus.ENABLED);
        assertDoesNotThrow(() -> user.setTwoFactorStatus(null));
        assertEquals(User.TwoFactorStatus.DISABLED, user.getTwoFactorStatus());
    }
}