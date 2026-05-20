import client.network.NetworkService;
import client.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Unit Tests cho UserService.
 *
 * SAU KHI REFACTOR:
 *   - LogOut() → logout() (Java camelCase — đây là lý do compile error)
 */
@DisplayName("UserService — Network Command Tests")
class UserServiceTest {

    @Test
    @DisplayName("logout() phải gửi command LOGOUT (FIX: was LogOut())")
    void logout_shouldSendLogoutCommand() {
        try (MockedStatic<NetworkService> mockedNetwork = mockStatic(NetworkService.class)) {
            // Act — dùng tên mới logout() (không phải LogOut())
            UserService.logout();

            // Assert
            mockedNetwork.verify(
                    () -> NetworkService.sendMessage("LOGOUT", ""),
                    times(1)
            );
        }
    }

    @Test
    @DisplayName("logout() chỉ gửi đúng 1 lần, không gọi bất kỳ command nào khác")
    void logout_shouldNotSendAnyOtherCommand() {
        try (MockedStatic<NetworkService> mockedNetwork = mockStatic(NetworkService.class)) {
            UserService.logout();

            // Tổng cộng chỉ 1 lần gọi sendMessage — không có side effect nào khác
            mockedNetwork.verify(
                    () -> NetworkService.sendMessage(any(), any()),
                    times(1)
            );
        }
    }
}


