package model.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Wallet — Domain Model Logic Tests")
class WalletTest {

    private Wallet wallet;
    private static final String USER_ID = "USER-123";
    private static final long INITIAL_BALANCE = 500_000L;

    @BeforeEach
    void setUp() {
        wallet = new Wallet(USER_ID, INITIAL_BALANCE);
    }

    @Test
    @DisplayName("Constructor: fields should be initialized correctly")
    void constructorInitialization() {
        assertEquals(USER_ID, wallet.getUserId());
        assertEquals(INITIAL_BALANCE, wallet.getBalance());
    }

    @Test
    @DisplayName("Default Constructor: fields should be default or null")
    void defaultConstructor() {
        Wallet empty = new Wallet();
        assertNull(empty.getUserId());
        assertEquals(0L, empty.getBalance());
    }

    @Test
    @DisplayName("Getters and Setters should map correctly")
    void gettersAndSetters() {
        Wallet empty = new Wallet();
        empty.setUserId("NEW-USER");
        empty.setBalance(1_000_000L);

        assertEquals("NEW-USER", empty.getUserId());
        assertEquals(1_000_000L, empty.getBalance());
    }

    @Test
    @DisplayName("hasSufficientFunds: should return true when balance is strictly greater than amount")
    void hasSufficientFunds_strictlyGreater() {
        assertTrue(wallet.hasSufficientFunds(300_000L));
    }

    @Test
    @DisplayName("hasSufficientFunds: should return true when balance is exactly equal to amount")
    void hasSufficientFunds_exactlyEqual() {
        assertTrue(wallet.hasSufficientFunds(500_000L));
    }

    @Test
    @DisplayName("hasSufficientFunds: should return false when balance is less than amount")
    void hasSufficientFunds_lessThan() {
        assertFalse(wallet.hasSufficientFunds(500_001L));
        assertFalse(wallet.hasSufficientFunds(1_000_000L));
    }
}
