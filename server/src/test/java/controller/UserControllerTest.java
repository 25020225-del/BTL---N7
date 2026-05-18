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

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserDAO userDAO;

    // TOTPService vẫn được giữ lại trong @InjectMocks,
    // nhưng KHÔNG stubbing nó ở đây vì register() không còn gọi tới nó nữa.
    // Xóa stub cũ "when(totpService.createSecretKey()).thenReturn(...)" để tránh
    // UnnecessaryStubbingException từ Mockito strict mode.
    @Mock
    private TOTPService totpService;

    @InjectMocks
    private UserController userController;

    /**
     * Kịch bản: Người dùng đăng ký với tên đăng nhập đã tồn tại trong DB.
     * Mong đợi: UserController bắt SQLite Integrity Constraint Violation (code 19)
     * và trả về thông báo thân thiện thay vì ném exception ra ngoài.
     */
    @Test
    void register_DuplicateUsername_ReturnsConstraintErrorMessage() throws SQLException {
        // ── Arrange ──────────────────────────────────────────────────────────────
        String userName = "kien_n7";

        // Mô phỏng SQLite ném lỗi UNIQUE constraint (mã lỗi 19, SQLState "23000")
        SQLException mockSqlException = new SQLException(
                "UNIQUE constraint failed", "23000", 19);

        // FIX: createUserAndWallet hiện chỉ nhận 6 tham số sau khi bỏ secretKey.
        // Trước đây (7 tham số — có secretKey):
        //   doThrow(...).when(userDAO).createUserAndWallet(
        //       any(Connection.class), anyString(), eq(userName),
        //       anyString(), anyString(), anyString(), anyString()   ← 7 args, SAI
        //   );
        // Bây giờ (6 tham số — không có secretKey):
        doThrow(mockSqlException)
                .when(userDAO)
                .createUserAndWallet(
                        any(Connection.class), // conn
                        anyString(),           // userId  (được tạo nội bộ bằng System.currentTimeMillis)
                        eq(userName),          // userName
                        anyString(),           // hashedPassword (được BCrypt nội bộ)
                        anyString(),           // name
                        anyString()            // role
                );

        // ── Act ──────────────────────────────────────────────────────────────────
        String result = userController.register(userName, "password123", "Kiên", "USER");

        // ── Assert ───────────────────────────────────────────────────────────────
        // Controller phải bắt exception và trả về thông báo thân thiện với người dùng
        assertEquals(
                "Tên đăng nhập đã tồn tại. Vui lòng chọn tên khác!",
                result,
                "Controller phải xử lý UNIQUE constraint và trả về thông báo đúng."
        );
    }
}