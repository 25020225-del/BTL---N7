package gui.process;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;

import java.util.Optional;

/**
 * Centralized utility for all UI-facing notifications in the Auction System client.
 *
 * <p><b>Vấn đề giải quyết:</b> Thay thế hoàn toàn pattern {@code logger.error(msg)}
 * (chỉ ghi log, người dùng không thấy gì) bằng các thông báo hiển thị trực tiếp
 * trên giao diện, đảm bảo an toàn với JavaFX UI Thread.</p>
 *
 * <h3>Cách sử dụng chuẩn:</h3>
 * <pre>{@code
 * // Trong bất kỳ Controller nào — từ UI Thread:
 * AlertUtils.showError("Lỗi Xác Thực", "Vui lòng điền đầy đủ các trường bắt buộc.");
 *
 * // Từ background thread (Socket callback, Task, v.v.):
 * AlertUtils.showErrorSafe("Mất kết nối", "Không thể kết nối tới server.");
 *
 * // Hiển thị lỗi inline trên Label trong form (không dùng popup):
 * AlertUtils.showInlineError(lblError, "Giá tối thiểu là 2.000 VNĐ.");
 * }</pre>
 *
 * <h3>Chiến lược thiết kế:</h3>
 * <ul>
 *   <li>Tất cả phương thức {@code show*()} đều an toàn để gọi từ UI Thread.</li>
 *   <li>Tất cả phương thức {@code show*Safe()} sử dụng {@link Platform#runLater}
 *       nên an toàn để gọi từ bất kỳ thread nào (Socket, Task, v.v.).</li>
 *   <li>{@code showInlineError()} / {@code clearInlineError()} cung cấp UX mượt hơn
 *       cho form validation — lỗi hiện ngay tại chỗ, không dùng popup.</li>
 * </ul>
 */
public final class AlertUtils {

    // CSS inline style cho Label lỗi — dùng màu đỏ chuẩn Material Design
    private static final String ERROR_LABEL_STYLE =
            "-fx-text-fill: #D32F2F; -fx-font-size: 12px; -fx-font-weight: bold;";
    private static final String WARNING_LABEL_STYLE =
            "-fx-text-fill: #F57C00; -fx-font-size: 12px; -fx-font-weight: bold;";

    // Ngăn khởi tạo instance — đây là utility class thuần static
    private AlertUtils() {
        throw new UnsupportedOperationException("AlertUtils is a static utility class.");
    }

    // =========================================================================
    // PHẦN 1: POPUP ALERT — Gọi từ JavaFX UI Thread
    // =========================================================================

    /**
     * Hiển thị popup lỗi (ERROR). Gọi từ UI Thread.
     *
     * @param title   Tiêu đề popup (ví dụ: "Lỗi Xác Thực")
     * @param content Nội dung chi tiết thông báo lỗi
     */
    public static void showError(String title, String content) {
        showAlert(Alert.AlertType.ERROR, title, content);
    }

    /**
     * Hiển thị popup thông tin (INFORMATION). Gọi từ UI Thread.
     *
     * @param title   Tiêu đề popup
     * @param content Nội dung thông báo
     */
    public static void showInfo(String title, String content) {
        showAlert(Alert.AlertType.INFORMATION, title, content);
    }

    /**
     * Hiển thị popup cảnh báo (WARNING). Gọi từ UI Thread.
     *
     * @param title   Tiêu đề popup
     * @param content Nội dung cảnh báo
     */
    public static void showWarning(String title, String content) {
        showAlert(Alert.AlertType.WARNING, title, content);
    }

    /**
     * Hiển thị popup xác nhận (CONFIRMATION) và trả về lựa chọn của người dùng.
     * Gọi từ UI Thread.
     *
     * <p>Ví dụ: Hỏi "Bạn có chắc muốn đặt giá không?" trước khi gửi lệnh.</p>
     *
     * @param title   Tiêu đề popup
     * @param content Câu hỏi xác nhận
     * @return {@code true} nếu người dùng bấm OK/Yes, {@code false} nếu bấm Cancel
     */
    public static boolean showConfirmation(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    // =========================================================================
    // PHẦN 2: THREAD-SAFE VARIANTS — Gọi từ bất kỳ thread nào (Socket, Task)
    // =========================================================================

    /**
     * Hiển thị popup lỗi AN TOÀN từ bất kỳ thread nào.
     *
     * <p><b>Dùng khi nào:</b> Trong các callback của Socket, background Task,
     * hoặc EventBus listener có thể chạy ngoài UI Thread.</p>
     *
     * <pre>{@code
     * // Ví dụ trong NetworkService callback:
     * socket.onError(e -> AlertUtils.showErrorSafe("Mất kết nối", e.getMessage()));
     * }</pre>
     *
     * @param title   Tiêu đề popup
     * @param content Nội dung lỗi
     */
    public static void showErrorSafe(String title, String content) {
        runOnUiThread(() -> showError(title, content));
    }

    /**
     * Hiển thị popup thông tin AN TOÀN từ bất kỳ thread nào.
     *
     * @param title   Tiêu đề popup
     * @param content Nội dung thông báo
     */
    public static void showInfoSafe(String title, String content) {
        runOnUiThread(() -> showInfo(title, content));
    }

    /**
     * Hiển thị popup cảnh báo AN TOÀN từ bất kỳ thread nào.
     *
     * @param title   Tiêu đề popup
     * @param content Nội dung cảnh báo
     */
    public static void showWarningSafe(String title, String content) {
        runOnUiThread(() -> showWarning(title, content));
    }

    // =========================================================================
    // PHẦN 3: INLINE LABEL ERROR — UX tốt hơn cho Form Validation
    // =========================================================================

    /**
     * Hiển thị thông báo lỗi INLINE trực tiếp trên một {@link Label} trong form.
     *
     * <p><b>Ưu điểm so với popup:</b> Người dùng thấy lỗi ngay tại chỗ mà không bị
     * ngắt luồng bởi popup. Phù hợp cho form validation lặp đi lặp lại.</p>
     *
     * <p><b>Yêu cầu FXML:</b> Thêm một Label ẩn vào form:</p>
     * <pre>{@code
     * <Label fx:id="lblError" visible="false" managed="false"
     *        wrapText="true" maxWidth="400"/>
     * }</pre>
     *
     * @param label   Label được khai báo với {@code @FXML} trong Controller
     * @param message Nội dung thông báo lỗi
     */
    public static void showInlineError(Label label, String message) {
        if (label == null) {
            return;
        }
        label.setText(message);
        label.setStyle(ERROR_LABEL_STYLE);
        label.setVisible(true);
        label.setManaged(true);
    }

    /**
     * Hiển thị cảnh báo INLINE trực tiếp trên một {@link Label}.
     *
     * @param label   Label target
     * @param message Nội dung cảnh báo
     */
    public static void showInlineWarning(Label label, String message) {
        if (label == null) {
            return;
        }
        label.setText(message);
        label.setStyle(WARNING_LABEL_STYLE);
        label.setVisible(true);
        label.setManaged(true);
    }

    /**
     * Ẩn và xóa nội dung của Label lỗi inline (gọi khi bắt đầu validate lại).
     *
     * @param label Label cần xóa
     */
    public static void clearInlineError(Label label) {
        if (label == null) {
            return;
        }
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }

    // =========================================================================
    // PHẦN 4: XỬ LÝ LỖI KẾT NỐI MẠNG — Network/Socket Errors
    // =========================================================================

    /**
     * Hiển thị thông báo lỗi kết nối mạng chuẩn hóa.
     *
     * <p>Tự động gọi {@link Platform#runLater} — an toàn từ mọi thread.
     * Dùng thống nhất ở mọi nơi xử lý {@code SocketException}, {@code IOException}.</p>
     *
     * @param context Mô tả ngắn về hành động đang thực hiện (ví dụ: "đặt giá",
     *                "tạo phiên đấu giá", "nạp tiền")
     */
    public static void showNetworkError(String context) {
        showErrorSafe(
                "Lỗi Kết Nối Mạng",
                "Không thể thực hiện '" + context + "'. "
                        + "Vui lòng kiểm tra kết nối mạng và thử lại."
        );
    }

    /**
     * Hiển thị thông báo lỗi server chuẩn hóa khi server trả về lỗi.
     *
     * @param serverMessage Nội dung lỗi từ server response
     */
    public static void showServerError(String serverMessage) {
        showErrorSafe("Lỗi Từ Máy Chủ", serverMessage);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Tạo và hiển thị Alert dialog cơ bản. Tất cả phương thức public đều
     * route qua đây để đảm bảo format thống nhất.
     */
    private static void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Kiểm tra và đảm bảo code chạy trên JavaFX Application Thread.
     * Nếu đang ở UI Thread: chạy trực tiếp (tránh double-scheduling).
     * Nếu đang ở thread khác: dùng Platform.runLater().
     */
    private static void runOnUiThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}