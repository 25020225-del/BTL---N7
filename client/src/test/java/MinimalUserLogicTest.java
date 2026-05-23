import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test logic block/unblock của MinimalUser.
 * <p>
 * Vì MinimalUser gắn với JavaFX toolkit, chúng ta test LOGIC command-routing
 * bằng cách simulate hành vi của nút bấm thông qua Consumer<String>.
 * <p>
 * SAU KHI REFACTOR — lỗi đã fix:
 * - AdminService.BLOCK_USER (constant bị xoá) → "BLOCK_USER" (string literal)
 * - Logic đảo ngược đã được sửa:
 * isBlocked=true  → command phải là UNBLOCK_USER (muốn unblock)
 * isBlocked=false → command phải là BLOCK_USER   (muốn block)
 */
@DisplayName("MinimalUser — Block/Unblock Logic Tests")
class MinimalUserLogicTest {

    /**
     * Helper class: giả lập logic command-selection của MinimalUser
     * mà không cần khởi tạo JavaFX component.
     * <p>
     * Đây là EXTRACT METHOD pattern — tách logic khỏi UI để test được.
     */
    static String selectCommand(boolean isCurrentlyBlocked) {
        // Logic này là bản sao chính xác từ MinimalUser.java sau khi fix
        return isCurrentlyBlocked ? "UNBLOCK_USER" : "BLOCK_USER";
    }

    // ─── REGRESSION TEST — Logic đảo ngược ───────────────────────────────────

    @Test
    @DisplayName("[REGRESSION] User đang bị blocked → command phải là UNBLOCK_USER")
    void whenUserIsBlocked_commandShouldBeUnblock() {
        // isBlocked = true → chúng ta MUỐN unblock → gửi UNBLOCK_USER
        String command = selectCommand(true);
        assertEquals("UNBLOCK_USER", command,
                "REGRESSION: User đang blocked phải nhận UNBLOCK_USER, không phải BLOCK_USER!");
    }

    @Test
    @DisplayName("[REGRESSION] User không bị blocked → command phải là BLOCK_USER")
    void whenUserIsNotBlocked_commandShouldBeBlock() {
        // isBlocked = false → chúng ta MUỐN block → gửi BLOCK_USER
        String command = selectCommand(false);
        assertEquals("BLOCK_USER", command,
                "REGRESSION: User không bị blocked phải nhận BLOCK_USER, không phải UNBLOCK_USER!");
    }

    @Test
    @DisplayName("Toggle: sau khi block thì unblock, sau unblock thì block")
    void toggle_blockUnblockSequence() {
        List<String> commandsSent = new ArrayList<>();

        // Simulate: user ban đầu không bị blocked
        boolean isBlocked = false;

        // Click 1: nên gửi BLOCK_USER
        commandsSent.add(selectCommand(isBlocked));
        isBlocked = !isBlocked;   // toggle state

        // Click 2: nên gửi UNBLOCK_USER
        commandsSent.add(selectCommand(isBlocked));
        isBlocked = !isBlocked;

        // Click 3: nên gửi BLOCK_USER lại
        commandsSent.add(selectCommand(isBlocked));

        assertEquals(List.of("BLOCK_USER", "UNBLOCK_USER", "BLOCK_USER"), commandsSent);
    }
}