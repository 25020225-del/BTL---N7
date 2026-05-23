package client.service;

import client.network.NetworkService;

/**
 * Service class encapsulating user-account-level network commands.
 *
 * <p>This is a stateless utility class and must not be instantiated.</p>
 */
public final class UserService {

    /**
     * Private constructor — utility class, not instantiable.
     */
    private UserService() {
    }

    /**
     * Sends a logout request to the server for the currently authenticated user session.
     *
     * <p><b>FIX (Naming Convention):</b> Renamed from {@code LogOut()} to {@code logout()}
     * to comply with the Java method naming convention (camelCase, starting with lowercase).</p>
     */
    public static void logout() { // FIX: was LogOut()
        NetworkService.sendMessage("LOGOUT", "");
    }
}
