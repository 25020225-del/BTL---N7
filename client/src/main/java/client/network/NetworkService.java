package client.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A static service holder that manages the single, application-wide {@link NetworkClient} instance.
 *
 * <p>This class follows the <b>Service Locator</b> pattern, providing a central point to
 * access or update the global network connection. It is intentionally not a singleton to
 * allow the connection to be replaced (e.g., when reconnecting).</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * // Set during app initialization
 * NetworkService.set(ServerDiscovery.establishConnection(properties));
 *
 * // Use anywhere in the application
 * NetworkService.sendMessage("PLACE_BID", bidData);
 * }</pre>
 */
public final class NetworkService {

    private static final Logger log = LoggerFactory.getLogger(NetworkService.class); // FIX: was ClientUserController.class

    /**
     * The globally shared, active network client session. May be null before connection is established.
     */
    private static NetworkClient instance;

    /**
     * Private constructor — this is a utility class and must not be instantiated.
     */
    private NetworkService() {
    }

    /**
     * Returns the current active {@link NetworkClient} instance.
     *
     * @return The active client, or {@code null} if not yet initialized.
     */
    public static NetworkClient get() {
        return instance;
    }

    /**
     * Sets (or replaces) the global network client instance.
     * Typically called once during application startup by {@link ServerDiscovery}.
     *
     * @param client The newly established {@link NetworkClient}.
     */
    public static void set(NetworkClient client) {
        instance = client;
    }

    /**
     * Sends a command and its associated data payload to the server via the active client.
     * Logs an error and does nothing if the client is not connected.
     *
     * @param command The server command string (e.g., "LOGIN", "PLACE_BID").
     * @param data    The data payload to serialize and send.
     */
    public static void sendMessage(String command, Object data) {
        if (instance == null) {
            log.error("Cannot send command \"{}\": NetworkClient has not been initialized.", command);
            return;
        }
        instance.sendMessage(command, data);
    }
}
