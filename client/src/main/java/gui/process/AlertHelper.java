package gui.process;
import javafx.scene.control.Alert;

/**
 * A utility class to simplify the creation and display of JavaFX {@link Alert} dialogs.
 * This helper reduces boilerplate code when showing standard notifications, warnings,
 * or error messages to the user.
 */
public class AlertHelper {

    /**
     * Creates and displays a modal alert dialog and blocks the execution until the user closes it.
     *
     * @param type    The specific type of the alert (e.g., INFORMATION, WARNING, ERROR),
     *                which determines the default icon and behavior.
     * @param title   The text to be displayed in the title bar of the alert window.
     * @param content The main message text to be displayed inside the alert dialog.
     */
    public static void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}