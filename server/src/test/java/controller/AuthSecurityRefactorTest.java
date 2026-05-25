package controller;

import database.dao.UserDAO;
import exception.AuctionExceptions;
import model.user.User;
import network.ErrorPayload;
import network.NetworkMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import server.ClientHandler;
import server.handler.AuthHandler;
import service.PasswordResetService;
import service.TOTPService;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Lead Automation Tester Suite verifying Authentication & Advanced Security refactoring fixes.
 */
@ExtendWith(MockitoExtension.class)
class AuthSecurityRefactorTest {

    @Mock
    private UserDAO userDAO;

    @Mock
    private TOTPService totpService;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private ClientHandler clientHandler;

    @Mock
    private UserController userController;

    private AuthHandler authHandler;

    @BeforeEach
    void setUp() {
        authHandler = new AuthHandler();
        lenient().when(clientHandler.getUserController()).thenReturn(userController);
    }

    /**
     * Test Case 1: Privilege Escalation Interception.
     * Asserts that even if a client attempts to register with role = "ADMIN",
     * the role parameter written to the database is strictly overridden to "USER".
     */
    @Test
    void testPrivilegeEscalation_EnforcesUserRole() throws SQLException {
        // Instantiate real UserController to trace concrete execution flow down to UserDAO
        UserController realController = new UserController(userDAO, totpService, passwordResetService);

        String username = "attacker";
        String password = "StrongPass123!";
        String name = "Attacker Name";
        String attackerRole = "ADMIN";

        // Call register method with privileged ADMIN role
        realController.register(username, password, name, attackerRole);

        // Capture parameters passed to the userDAO insertion statement
        ArgumentCaptor<String> roleCaptor = ArgumentCaptor.forClass(String.class);
        verify(userDAO).createUserAndWallet(
                any(Connection.class),
                anyString(),
                eq(username),
                anyString(),
                eq(name),
                roleCaptor.capture()
        );

        // Verify that the privilege escalation was intercepted and strictly overridden to USER
        assertEquals("USER", roleCaptor.getValue(), "User registration role must be strictly hardcoded/overridden to USER");
    }

    /**
     * Test Case 2: Thread Leak Protection.
     * Verifies that the ScheduledExecutorService cleaner thread pool in PasswordResetService
     * is successfully shut down when the shutdown method is called.
     */
    @Test
    void testThreadLeakProtection_CleanerGracefulShutdown() throws Exception {
        PasswordResetService resetService = new PasswordResetService();

        // Extract private cleaner executor using reflection to assert state transitions
        java.lang.reflect.Field cleanerField = PasswordResetService.class.getDeclaredField("cleaner");
        cleanerField.setAccessible(true);
        ScheduledExecutorService cleaner = (ScheduledExecutorService) cleanerField.get(resetService);

        assertNotNull(cleaner, "Scheduled cleaner executor must be initialized");
        assertFalse(cleaner.isShutdown(), "Cleaner executor must be active initially");

        // Execute graceful shutdown
        resetService.shutdown();

        // Assert that the cleaner is successfully shutdown, avoiding thread/scheduler leaks
        assertTrue(cleaner.isShutdown(), "Cleaner executor must be successfully shut down to avoid Thread Leak");
    }

    /**
     * Test Case 3: Empty & Null Data Robustness Validation (NPE Protection).
     * Simulates client registrations and logins with null or empty attributes,
     * assuring the system returns a proper validation error response (like ERR_AUTH_003/ERR_AUTH_005)
     * instead of throwing unhandled NullPointerExceptions or thread crashes.
     */
    @Test
    void testRegistrationAndLogin_WithNullOrBlankFields_ReturnsCleanValidationErrors() throws Exception {
        // 3a. Test Login with null fields
        Map<String, Object> emptyLoginData = new HashMap<>();
        emptyLoginData.put("userName", null);
        emptyLoginData.put("password", "   ");

        // Handle processLogin
        authHandler.handle(new NetworkMessage("LOGIN", emptyLoginData), clientHandler);

        ArgumentCaptor<String> errorCmdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ErrorPayload> errorPayloadCaptor = ArgumentCaptor.forClass(ErrorPayload.class);
        verify(clientHandler).sendResponse(errorCmdCaptor.capture(), errorPayloadCaptor.capture());

        assertEquals("ERROR", errorCmdCaptor.getValue());
        assertEquals("ERR_AUTH_003", errorPayloadCaptor.getValue().getErrorCode());
        assertEquals("Tên đăng nhập và mật khẩu không được để trống.", errorPayloadCaptor.getValue().getErrorMessage());

        // Reset mock to test registration
        reset(clientHandler);
        lenient().when(clientHandler.getUserController()).thenReturn(userController);

        // 3b. Test Registration with null username
        Map<String, Object> invalidRegData = new HashMap<>();
        invalidRegData.put("userName", null);
        invalidRegData.put("password", "Strong1!");
        invalidRegData.put("name", "Good User");
        invalidRegData.put("role", "USER");

        when(userController.register(isNull(), anyString(), eq("Good User"), anyString()))
                .thenReturn("Tên đăng nhập không được để trống.");

        authHandler.handle(new NetworkMessage("REGISTER", invalidRegData), clientHandler);

        verify(clientHandler).sendResponse(errorCmdCaptor.capture(), errorPayloadCaptor.capture());
        assertEquals("ERROR", errorCmdCaptor.getValue());
        assertEquals("ERR_AUTH_005", errorPayloadCaptor.getValue().getErrorCode());
        assertEquals("Tên đăng nhập không được để trống.", errorPayloadCaptor.getValue().getErrorMessage());
    }
}
