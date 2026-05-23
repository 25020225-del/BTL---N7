import client.network.NetworkService;
import client.service.AdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.*;

/**
 * Unit Tests cho AdminService.
 * <p>
 * AdminService là static utility class gọi NetworkService.sendMessage() (cũng static).
 * → Cần dùng Mockito MockedStatic để intercept lời gọi static.
 * <p>
 * ĐẶT FILE NÀY Ở: client/src/test/java/client/service/AdminServiceTest.java
 * <p>
 * DEPENDENCY cần trong client/pom.xml:
 * - mockito-inline (version 5.2.0) — cho phép mock static methods
 * - junit-jupiter (version 5.11.0)
 * <p>
 * SAU KHI REFACTOR — những thay đổi cần biết:
 * - blockUser(String command, String id) → blockUser(String id) [bỏ param command]
 * - unblockUser(String id) — method mới hoàn toàn
 * - Constants BLOCK_USER, UNBLOCK_USER bị XOÁ khỏi AdminService
 */
@DisplayName("AdminService — Network Command Tests")
class AdminServiceTest {

    // ─── blockUser() ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("blockUser() phải gửi command BLOCK_USER với đúng userId")
    void blockUser_shouldSendCorrectCommand() {
        try (MockedStatic<NetworkService> mockedNetwork = mockStatic(NetworkService.class)) {
            // Act
            AdminService.blockUser("user-42");

            // Assert: verify NetworkService.sendMessage("BLOCK_USER", "user-42") được gọi đúng 1 lần
            mockedNetwork.verify(
                    () -> NetworkService.sendMessage("BLOCK_USER", "user-42"),
                    times(1)
            );
        }
    }

    @Test
    @DisplayName("blockUser() không được gọi UNBLOCK_USER hay lệnh khác")
    void blockUser_shouldNotCallUnblock() {
        try (MockedStatic<NetworkService> mockedNetwork = mockStatic(NetworkService.class)) {
            AdminService.blockUser("user-42");

            // Verify UNBLOCK_USER KHÔNG được gọi (test logic đảo ngược đã fix)
            mockedNetwork.verify(
                    () -> NetworkService.sendMessage(eq("UNBLOCK_USER"), any()),
                    never()
            );
        }
    }

    // ─── unblockUser() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("unblockUser() phải gửi command UNBLOCK_USER với đúng userId")
    void unblockUser_shouldSendCorrectCommand() {
        try (MockedStatic<NetworkService> mockedNetwork = mockStatic(NetworkService.class)) {
            AdminService.unblockUser("user-99");

            mockedNetwork.verify(
                    () -> NetworkService.sendMessage("UNBLOCK_USER", "user-99"),
                    times(1)
            );
        }
    }

    @Test
    @DisplayName("unblockUser() không được gọi BLOCK_USER")
    void unblockUser_shouldNotCallBlock() {
        try (MockedStatic<NetworkService> mockedNetwork = mockStatic(NetworkService.class)) {
            AdminService.unblockUser("user-99");

            mockedNetwork.verify(
                    () -> NetworkService.sendMessage(eq("BLOCK_USER"), any()),
                    never()
            );
        }
    }

    // ─── Các commands khác ───────────────────────────────────────────────────

    @Test
    @DisplayName("fetchPendingAuctions() gửi FETCH_PENDING_AUCTIONS")
    void fetchPendingAuctions_shouldSendCorrectCommand() {
        try (MockedStatic<NetworkService> mockedNetwork = mockStatic(NetworkService.class)) {
            AdminService.fetchPendingAuctions();
            mockedNetwork.verify(
                    () -> NetworkService.sendMessage("FETCH_PENDING_AUCTIONS", ""),
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