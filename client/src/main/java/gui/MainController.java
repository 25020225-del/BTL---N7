package gui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import model.user.User;

import java.io.IOException;

/**
 * Main routing controller that directs authenticated users to their appropriate dashboards.
 */
public class MainController {

    /**
     * Starts the appropriate UI controller based on the user's role.
     *
     * @param user The authenticated user object containing role information.
     * @throws IOException If the FXML files fail to load.
     */
    public static void start(User user) throws IOException {
        if (user.getRole().equalsIgnoreCase("ADMIN")) startAdmin(user);
        else startUnifiedUser(user);
    }

    /**
     * Initializes the unified dashboard for standard users (Bidders & Sellers).
     */
    private static void startUnifiedUser(User user) throws IOException {
        ClientUserController controller = new ClientUserController(user);
        controller.start();
    }

    /**
     * Initializes the dashboard for system administrators.
     */
    private static void startAdmin(User user) throws IOException {
        ClientAdminController controller = new ClientAdminController(user);
        controller.start();
    }
}