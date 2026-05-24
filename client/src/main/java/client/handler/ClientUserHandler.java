package client.handler;

import client.network.NetworkClient;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A stateless event relay that intercepts user-scoped server responses and propagates
 * them directly onto the localized {@link AuctionEventBus}.
 * This component decouples network ingress infrastructure from UI controller lifecycles.
 */
public class ClientUserHandler implements ResponseHandler {

    private static final Logger log = LoggerFactory.getLogger(ClientUserHandler.class);

    @Override
    public void handle(NetworkMessage message, NetworkClient client) {
        String command = message.getCommand();
        log.debug("Relaying user event to EventBus: {}", command);
        AuctionEventBus.fireEvent(command, message);
    }
}