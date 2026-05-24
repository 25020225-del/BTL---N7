package gui;

import model.user.User;
import java.io.IOException;

/**
 * Top-level application router intercepting post-authentication state tokens.
 * Directs individual identity frameworks toward their respective specialized system workspaces.
 */
public class MainController {

    /**
     * Resolves role privileges from identity claims and maps execution onto the appropriate dashboard interface.
     *
     * @param user the validated profile entity token containing authorization claims
     * @throws IOException if the target dashboard graphical structure fails configuration parameters
     */
    public static void start(User user) throws IOException {
        if (user.getRole().equalsIgnoreCase("ADMIN")) {
            startAdmin(user);
        } else {
            startUnifiedUser(user);
        }
    }

    private static void startUnifiedUser(User user) throws IOException {
        ClientUserController controller = new ClientUserController(user);
        controller.start();
    }

    private static void startAdmin(User user) throws IOException {
        ClientAdminController controller = new ClientAdminController(user);
        controller.start();
    }
}