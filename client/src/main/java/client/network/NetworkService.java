package client.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global service locator serving as the centralized access point for the application's
 * active {@link NetworkClient} context. Decouples stateful communication components
 * from UI and transient domain contexts.
 */
public final class NetworkService {

    private static final Logger log = LoggerFactory.getLogger(NetworkService.class);
    private static NetworkClient instance;

    private NetworkService() {
    }

    public static NetworkClient get() {
        return instance;
    }

    public static void set(NetworkClient client) {
        instance = client;
    }

    /**
     * Dispatches an outbound command and its generic data payload through the active transport channel.
     * Logs an error boundary message if execution is attempted before subsystem initialization.
     *
     * @param command the remote operational code targeting a server-side endpoint
     * @param data    the state object to be serialized and transmitted
     */
    public static void sendMessage(String command, Object data) {
        if (instance == null) {
            log.error("Cannot send command \"{}\": NetworkClient has not been initialized.", command);
            return;
        }
        instance.sendMessage(command, data);
    }
}