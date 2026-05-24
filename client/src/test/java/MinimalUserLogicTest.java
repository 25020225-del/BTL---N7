import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test suite validating command-routing behaviors inside decoupled presentation frames.
 * Isolates user action toggles using extract method simulations independent from active UI toolkits.
 */
@DisplayName("MinimalUser — Block/Unblock Logic Tests")
class MinimalUserLogicTest {

    static String selectCommand(boolean isCurrentlyBlocked) {
        return isCurrentlyBlocked ? "UNBLOCK_USER" : "BLOCK_USER";
    }

    @Test
    @DisplayName("[REGRESSION] User đang bị blocked → command phải là UNBLOCK_USER")
    void whenUserIsBlocked_commandShouldBeUnblock() {
        String command = selectCommand(true);
        assertEquals("UNBLOCK_USER", command,
                "REGRESSION: User đang blocked phải nhận UNBLOCK_USER, không phải BLOCK_USER!");
    }

    @Test
    @DisplayName("[REGRESSION] User không bị blocked → command phải là BLOCK_USER")
    void whenUserIsNotBlocked_commandShouldBeBlock() {
        String command = selectCommand(false);
        assertEquals("BLOCK_USER", command,
                "REGRESSION: User không bị blocked phải nhận BLOCK_USER, không phải UNBLOCK_USER!");
    }

    @Test
    @DisplayName("Toggle: sau khi block thì unblock, sau unblock thì block")
    void toggle_blockUnblockSequence() {
        List<String> commandsSent = new ArrayList<>();
        boolean isBlocked = false;

        commandsSent.add(selectCommand(isBlocked));
        isBlocked = !isBlocked;

        commandsSent.add(selectCommand(isBlocked));
        isBlocked = !isBlocked;

        commandsSent.add(selectCommand(isBlocked));

        assertEquals("BLOCK_USER", commandsSent.get(0));
        assertEquals("UNBLOCK_USER", commandsSent.get(1));
        assertEquals("BLOCK_USER", commandsSent.get(2));
    }
}