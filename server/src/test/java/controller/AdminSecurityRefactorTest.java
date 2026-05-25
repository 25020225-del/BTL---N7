package controller;

import database.DatabaseManager;
import database.TransactionManager;
import database.dao.AuctionDAO;
import database.dao.UserDAO;
import database.dao.WalletDAO;
import database.dao.WithdrawalDAO;
import model.auction.Auction;
import model.user.Admin;
import model.user.User;
import network.ErrorPayload;
import network.NetworkMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import server.ClientHandler;
import server.handler.AdminActionHandler;
import service.AdminAuctionService;
import service.AdminAuctionService.CancelResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Lead Automation Tester Suite verifying Administrative Features Refactoring fixes.
 */
@ExtendWith(MockitoExtension.class)
class AdminSecurityRefactorTest {

    @Mock
    private UserDAO userDAO;

    @Mock
    private AuctionDAO auctionDAO;

    @Mock
    private WalletDAO walletDAO;

    @Mock
    private WithdrawalDAO withdrawalDAO;

    @Mock
    private ClientHandler clientHandler;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    private ServerAdminController adminController;
    private AdminAuctionService adminAuctionService;
    private AdminActionHandler adminActionHandler;

    private Admin adminUser;

    @BeforeEach
    void setUp() {
        adminController = new ServerAdminController(userDAO, auctionDAO, walletDAO, withdrawalDAO);
        adminAuctionService = new AdminAuctionService(auctionDAO, walletDAO);
        adminActionHandler = new AdminActionHandler(auctionDAO, userDAO, adminController, adminAuctionService);

        adminUser = new Admin("admin-id", "adminUser", "pass", "Administrator");
    }

    /**
     * EC-301: Inactive Leader Bot Escrow Freeze Resolution.
     * Verifies that when an auction is cancelled, a winning bidder who deactivated their bot
     * is fully refunded their entire locked bot max_bid (using Math.max(highestMaxBid, max_bid)),
     * preventing any residual locked balance leaks.
     */
    @Test
    void testInactiveLeaderBotRefund_EC301_RefundsFullMaxBid() throws SQLException {
        try (MockedStatic<DatabaseManager> mockedDb = mockStatic(DatabaseManager.class)) {
            mockedDb.when(DatabaseManager::getConnection).thenReturn(connection);

            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);

            // Mock Read Auction Snapshot: status = 'OPEN', winnerUserId = 'user-a', highestMaxBid = 150000
            when(resultSet.next()).thenReturn(true, true, false, true, false);
            when(resultSet.getString("status")).thenReturn("OPEN");
            when(resultSet.getString("winning_bidder_id")).thenReturn("user-a");
            when(resultSet.getLong("highest_max_bid")).thenReturn(150000L);

            // Mock Mark Auction as Cancelled: affects 1 row
            when(preparedStatement.executeUpdate()).thenReturn(1);

            // Mock fetchWinnerAutoBidMaxBid: winner had an auto-bid record with max_bid = 300000
            when(resultSet.getLong("max_bid")).thenReturn(300000L);

            // Trigger the cancel operation
            CancelResult result = adminAuctionService.cancelAuctionAndRefund("AUC-001");

            assertEquals(CancelResult.SUCCESS, result);

            // Assert that the refund amount to user-a is exactly Math.max(highestMaxBid, max_bid) = 300000 VND
            verify(walletDAO).unlockBalance(eq(connection), eq("user-a"), eq(300000L));
            verify(connection, atLeastOnce()).commit();
            verify(connection, never()).rollback();
        }
    }

    /**
     * EC-302: Transaction Rollback Safety.
     * Verifies that if an unexpected runtime exception (like NullPointerException or ClassCastException)
     * is raised during the processing of a withdrawal, the system executes conn.rollback()
     * and returns "DB_ERROR" cleanly instead of leaving dangling database transaction locks.
     */
    @Test
    void testWithdrawalTransactionSafety_EC302_TriggersRollbackOnRuntimeException() throws Exception {
        try (MockedStatic<DatabaseManager> mockedDb = mockStatic(DatabaseManager.class);
             MockedStatic<TransactionManager> mockedTx = mockStatic(TransactionManager.class)) {
            mockedDb.when(DatabaseManager::getConnection).thenReturn(connection);

            mockedTx.when(() -> TransactionManager.submitTask(any())).thenAnswer(invocation -> {
                java.util.concurrent.Callable<?> task = invocation.getArgument(0);
                try {
                    Object result = task.call();
                    return CompletableFuture.completedFuture(result);
                } catch (Exception e) {
                    CompletableFuture<?> f = new CompletableFuture<>();
                    f.completeExceptionally(e);
                    return f;
                }
            });

            // Simulate the request returning a valid map
            Map<String, Object> malformedRequest = new HashMap<>();
            malformedRequest.put("userId", "user-a");
            malformedRequest.put("amount", 5000L);

            when(withdrawalDAO.getRequestByIdWithLock(eq(connection), eq("REQ-001"))).thenReturn(malformedRequest);
            // Stub deductFromLocked to throw a RuntimeException to trigger transaction rollback
            when(walletDAO.deductFromLocked(eq(connection), eq("user-a"), eq(5000L)))
                    .thenThrow(new RuntimeException("Simulated runtime fault"));

            // Execute the withdrawal process directly to examine transaction boundary transitions
            CompletableFuture<String> futureResult = adminController.processWithdrawal(adminUser, "REQ-001", true);
            String resultStr = futureResult.get();

            assertEquals("DB_ERROR", resultStr);
            verify(connection).rollback(); // rollback must be triggered on RuntimeException
            verify(connection, never()).commit();
        }
    }

    /**
     * EC-303: Concurrency Maker-Checker Double-Processing Prevention.
     * Verifies that concurrent attempts to approve the same withdrawal request result in
     * the first attempt completing successfully and the second one rolling back cleanly
     * returning NOT_FOUND/ALREADY_PROCESSED.
     */
    @Test
    void testConcurrencyMakerChecker_EC303_PreventsDoubleProcessing() throws Exception {
        try (MockedStatic<DatabaseManager> mockedDb = mockStatic(DatabaseManager.class);
             MockedStatic<TransactionManager> mockedTx = mockStatic(TransactionManager.class)) {
            mockedDb.when(DatabaseManager::getConnection).thenReturn(connection);

            mockedTx.when(() -> TransactionManager.submitTask(any())).thenAnswer(invocation -> {
                java.util.concurrent.Callable<?> task = invocation.getArgument(0);
                try {
                    Object result = task.call();
                    return CompletableFuture.completedFuture(result);
                } catch (Exception e) {
                    CompletableFuture<?> f = new CompletableFuture<>();
                    f.completeExceptionally(e);
                    return f;
                }
            });

            Map<String, Object> pendingRequest = new HashMap<>();
            pendingRequest.put("userId", "user-b");
            pendingRequest.put("amount", 2000L);

            // First call succeeds to lock and approve
            when(withdrawalDAO.getRequestByIdWithLock(eq(connection), eq("REQ-002"))).thenReturn(pendingRequest);
            when(walletDAO.deductFromLocked(eq(connection), eq("user-b"), eq(2000L))).thenReturn(true);
            when(withdrawalDAO.approveWithdrawal(eq(connection), eq("REQ-002"), anyString(), anyString())).thenReturn(true);

            CompletableFuture<String> future1 = adminController.processWithdrawal(adminUser, "REQ-002", true);
            assertEquals("SUCCESS", future1.get());

            // Second concurrent call fails because the database row status is no longer PENDING (returns false in approveWithdrawal)
            reset(walletDAO, withdrawalDAO);
            when(withdrawalDAO.getRequestByIdWithLock(eq(connection), eq("REQ-002"))).thenReturn(pendingRequest);
            when(walletDAO.deductFromLocked(eq(connection), eq("user-b"), eq(2000L))).thenReturn(true);
            when(withdrawalDAO.approveWithdrawal(eq(connection), eq("REQ-002"), anyString(), anyString())).thenReturn(false);

            CompletableFuture<String> future2 = adminController.processWithdrawal(adminUser, "REQ-002", true);
            assertEquals("NOT_FOUND", future2.get());
            verify(connection, atLeastOnce()).rollback(); // second attempt must roll back its deduction
        }
    }

    /**
     * EC-304: Malformed Socket Payload Validation.
     * Verifies that when malformed or null payloads are sent through the socket layer,
     * the AdminActionHandler catches them safely and returns clean ErrorPayload responses
     * instead of quashing/crashing the thread.
     */
    @Test
    void testMalformedPayloadHandling_EC304_ReturnsCleanErrors() throws Exception {
        User regularAdminUser = new User("admin-id", "adminUser", "pass", "Admin", "ADMIN");
        when(clientHandler.getUser()).thenReturn(regularAdminUser);

        // 4a. BLOCK_USER with null / blank data
        NetworkMessage msg1 = new NetworkMessage("BLOCK_USER", null);
        adminActionHandler.handle(msg1, clientHandler);

        ArgumentCaptor<ErrorPayload> errCaptor = ArgumentCaptor.forClass(ErrorPayload.class);
        verify(clientHandler).sendResponse(eq("ERROR"), errCaptor.capture());
        assertEquals("ERR_DB_005", errCaptor.getValue().getErrorCode());
        assertEquals("User ID không được để trống.", errCaptor.getValue().getErrorMessage());

        // 4b. CANCEL_AUCTION with empty string
        reset(clientHandler);
        when(clientHandler.getUser()).thenReturn(regularAdminUser);
        NetworkMessage msg2 = new NetworkMessage("CANCEL_AUCTION", "    ");
        adminActionHandler.handle(msg2, clientHandler);

        verify(clientHandler).sendResponse(eq("ERROR"), errCaptor.capture());
        assertEquals("ERR_ADMIN_013", errCaptor.getValue().getErrorCode());
        assertEquals("auctionId không được để trống.", errCaptor.getValue().getErrorMessage());
    }
}
