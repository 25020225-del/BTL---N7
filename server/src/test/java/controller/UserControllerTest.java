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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

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
        // Arrange: Giả lập dữ liệu đầu vào của một thành viên
        String userName = "kien_n7";
        when(totpService.createSecretKey()).thenReturn("DUMMY_SECRET_KEY");

        // Mô phỏng SQLite ném lỗi Integrity Constraint Violation (Mã lỗi 19)
        SQLException mockSqlException = new SQLException("UNIQUE constraint failed", "23000", 19);
        doThrow(mockSqlException).when(userDAO).createUserAndWallet(
                any(Connection.class), anyString(), eq(userName), anyString(), anyString(), anyString(), anyString()
        );

        // Act
        String result = userController.register(userName, "password123", "Kiên", "USER");

        // Assert: Controller phải bắt được ngoại lệ và trả về câu thông báo thân thiện
        assertEquals("Tên đăng nhập đã tồn tại. Vui lòng chọn tên khác!", result);
    }
}