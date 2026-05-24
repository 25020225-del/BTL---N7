import client.network.NetworkService;
import client.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.*;

/**
 * Unit test suite validating outbound session operation contexts for {@link UserService}.
 * Protects runtime registration message frameworks against side-effect command execution drops.
 */
@DisplayName("UserService — Network Command Tests")
class UserServiceTest {

    @Test
    @DisplayName("logout() phải gửi command LOGOUT (FIX: was LogOut())")
    void logout_shouldSendLogoutCommand() {
        try (MockedStatic<NetworkService> mockedNetwork = mockStatic(NetworkService.class)) {
            UserService.logout();

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

            mockedNetwork.verify(
                    () -> NetworkService.sendMessage(anyString(), anyString()),
                    times(1)
            );
        }
    }
}