package client.handler;

import client.network.NetworkClient;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles all user-specific server responses by forwarding them to the {@link AuctionEventBus}.
 *
 * <p>This handler acts as a transparent relay: it receives a {@link NetworkMessage} whose
 * command is the event name (e.g., {@code "FETCH_AUCTIONS_SUCCESS"}) and fires it directly
 * on the event bus. All interested UI controllers subscribe to those events independently,
 * keeping this handler stateless and single-purpose.</p>
 */
public class ClientUserHandler implements ResponseHandler {

    private static final Logger log = LoggerFactory.getLogger(ClientUserHandler.class);

    /**
     * Relays the incoming message to all registered {@link AuctionEventBus} listeners
     * under the message's own command as the event name.
     *
     * @param message The network message received from the server.
     * @param client  The active network client (not used by this handler).
     */
    @Override
    public void handle(NetworkMessage message, NetworkClient client) {
        String command = message.getCommand();
        log.debug("Relaying user event to EventBus: {}", command); // FIX: was System.out.println
        AuctionEventBus.fireEvent(command, message);
    }
}
