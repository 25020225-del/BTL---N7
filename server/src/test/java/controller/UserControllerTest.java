package controller;

import database.dao.UserDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import service.TOTPService;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;

/**
 * Unit tests for {@link UserController}.
 * Verifies user registration, authentication pathways, and constraint exception handling.
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserDAO userDAO;

    @Mock
    private TOTPService totpService;

    @InjectMocks
    private UserController userController;

    @Test
    void register_DuplicateUsername_ReturnsConstraintErrorMessage() throws SQLException {
        String userName = "kien_n7";

        SQLException mockSqlException = new SQLException(
                "UNIQUE constraint failed", "23000", 19);

        doThrow(mockSqlException)
                .when(userDAO)
                .createUserAndWallet(
                        any(Connection.class),
                        anyString(),
                        eq(userName),
                        anyString(),
                        anyString(),
                        anyString()
                );

        String result = userController.register(userName, "password123", "Kiên", "USER");

        assertEquals(
                "Tên đăng nhập đã tồn tại. Vui lòng chọn tên khác!",
                result,
                "Controller phải xử lý UNIQUE constraint và trả về thông báo đúng."
        );
    }
}