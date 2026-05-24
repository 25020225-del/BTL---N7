package gui.process;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;

import java.util.Optional;

/**
 * Unified UI presentation signaling manager. Houses modal dialog routing and inline
 * validation error displaying. Manages cross-thread scheduling to satisfy JavaFX application thread bounds.
 */
public final class AlertUtils {

    private static final String ERROR_LABEL_STYLE =
            "-fx-text-fill: #D32F2F; -fx-font-size: 12px; -fx-font-weight: bold;";
    private static final String WARNING_LABEL_STYLE =
            "-fx-text-fill: #F57C00; -fx-font-size: 12px; -fx-font-weight: bold;";

    private AlertUtils() {
        throw new UnsupportedOperationException("AlertUtils is a static utility class.");
    }

    /**
     * Dispatches a synchronous modal alert box of type ERROR on the current execution thread.
     */
    public static void showError(String title, String content) {
        showAlert(Alert.AlertType.ERROR, title, content);
    }

    /**
     * Dispatches a synchronous modal alert box of type INFORMATION on the current execution thread.
     */
    public static void showInfo(String title, String content) {
        showAlert(Alert.AlertType.INFORMATION, title, content);
    }

    /**
     * Dispatches a synchronous modal alert box of type WARNING on the current execution thread.
     */
    public static void showWarning(String title, String content) {
        showAlert(Alert.AlertType.WARNING, title, content);
    }

    /**
     * Captures binary user confirmation using a blocking modal interface window context.
     *
     * @return true if the affirmative confirmation token is selected, false otherwise
     */
    public static boolean showConfirmation(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Enqueues an execution packet to render an ERROR dialog safely from foreign asynchronous background task loops.
     */
    public static void showErrorSafe(String title, String content) {
        runOnUiThread(() -> showError(title, content));
    }

    /**
     * Enqueues an execution packet to render an INFORMATION dialog safely from foreign asynchronous background task loops.
     */
    public static void showInfoSafe(String title, String content) {
        runOnUiThread(() -> showInfo(title, content));
    }

    /**
     * Enqueues an execution packet to render a WARNING dialog safely from foreign asynchronous background task loops.
     */
    public static void showWarningSafe(String title, String content) {
        runOnUiThread(() -> showWarning(title, content));
    }

    /**
     * Updates an in-form inline visual node element to present structural input errors smoothly without popup intervention.
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
     * Updates an in-form inline visual node element to present data warning states directly into the container flow.
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
     * Clears inline warning properties and adjusts form spatial distribution constraints to hide metadata fields.
     */
    public static void clearInlineError(Label label) {
        if (label == null) {
            return;
        }
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }

    /**
     * Standardized error bridge transforming asynchronous IO connection context crashes into uniform client alert signals.
     */
    public static void showNetworkError(String context) {
        showErrorSafe(
                "Lỗi Kết Nối Mạng",
                "Không thể thực hiện '" + context + "'. Vui lòng kiểm tra kết nối mạng và thử lại."
        );
    }

    /**
     * Transforms remote server operational exceptions into visible user feedback contexts safely.
     */
    public static void showServerError(String serverMessage) {
        showErrorSafe("Lỗi Từ Máy Chủ", serverMessage);
    }

    private static void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private static void runOnUiThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}