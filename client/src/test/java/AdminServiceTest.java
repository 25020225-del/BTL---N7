import client.network.NetworkService;
import client.service.AdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.*;

/**
 * Unit test suite for {@link AdminService}.
 * Intercepts outbound global static integration handlers utilizing Mockito static mocking frameworks
 * to validate isolated technical network command dispatch operations.
 */
@DisplayName("AdminService — Network Command Tests")
class AdminServiceTest {

    @Test
    @DisplayName("blockUser() phải gửi command BLOCK_USER với đúng userId")
    void blockUser_shouldSendCorrectCommand() {
        try (MockedStatic<NetworkService> mockedNetwork = mockStatic(NetworkService.class)) {
            AdminService.blockUser("user-123");
            mockedNetwork.verify(
                    () -> NetworkService.sendMessage("BLOCK_USER", "user-123"),
                    times(1)
            );
        }
    }

    @Test
    @DisplayName("unblockUser() phải gửi command UNBLOCK_USER với đúng userId")
    void unblockUser_shouldSendCorrectCommand() {
        try (MockedStatic<NetworkService> mockedNetwork = mockStatic(NetworkService.class)) {
            AdminService.unblockUser("user-123");
            mockedNetwork.verify(
                    () -> NetworkService.sendMessage("UNBLOCK_USER", "user-123"),
                    times(1)
            );
        }
    }

    @Test
    @DisplayName("fetchUsers() gửi FETCH_USERS")
    void fetchUsers_shouldSendCorrectCommand() {
        try (MockedStatic<NetworkService> mockedNetwork = mockStatic(NetworkService.class)) {
            AdminService.fetchUsers();
            mockedNetwork.verify(
                    () -> NetworkService.sendMessage("FETCH_USERS", ""),
                    times(1)
            );
        }
    }

    @Test
    @DisplayName("approveAuction() gửi APPROVE_AUCTION với đúng auctionId")
    void approveAuction_shouldSendCorrectCommand() {
        try (MockedStatic<NetworkService> mockedNetwork = mockStatic(NetworkService.class)) {
            AdminService.approveAuction("auction-007");
            mockedNetwork.verify(
                    () -> NetworkService.sendMessage("APPROVE_AUCTION", "auction-007"),
                    times(1)
            );
        }
    }

    @Test
    @DisplayName("rejectAuction() gửi REJECT_AUCTION với đúng auctionId")
    void rejectAuction_shouldSendCorrectCommand() {
        try (MockedStatic<NetworkService> mockedNetwork = mockStatic(NetworkService.class)) {
            AdminService.rejectAuction("auction-007");
            mockedNetwork.verify(
                    () -> NetworkService.sendMessage("REJECT_AUCTION", "auction-007"),
                    times(1)
            );
        }
    }

    @Test
    @DisplayName("logout() gửi LOGOUT")
    void logout_shouldSendCorrectCommand() {
        try (MockedStatic<NetworkService> mockedNetwork = mockStatic(NetworkService.class)) {
            AdminService.logout();
            mockedNetwork.verify(
                    () -> NetworkService.sendMessage("LOGOUT", ""),
                    times(1)
            );
        }
    }
}