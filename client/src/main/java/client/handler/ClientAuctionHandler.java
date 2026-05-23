package client.handler;

import client.network.NetworkClient;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Handles auction-related responses and real-time push notifications broadcast by the server.
 *
 * <h2>Registered commands</h2>
 * <ul>
 *   <li>{@code UPDATE_AUCTION_PRICE}   — Real-time price update broadcast to all watchers.</li>
 *   <li>{@code AUTOBID_SETUP_SUCCESS}  — Server confirms an Auto-Bid was registered/updated/cancelled.</li>
 *   <li>{@code AUTOBID_ACTIVE}         — Server notifies that a bot is currently firing in an auction.</li>
 *   <li>{@code BID_SUCCESS}            — The user's own manual bid was accepted.</li>
 *   <li>{@code AUCTION_STATUS_CHANGED} — An auction moved to a new lifecycle state (ENDED, CANCELLED…).</li>
 *   <li>{@code CLI_BROADCAST}          — Generic server-wide text broadcast.</li>
 *   <li>{@code CREATE_SUCCESS}         — A new auction was created (for seller's UI).</li>
 *   <li>{@code CHAT}                   — In-auction chat message.</li>
 * </ul>
 *
 * <p>All events are forwarded through {@link AuctionEventBus} so UI controllers can
 * register / deregister listeners without coupling to the network layer.</p>
 */
public class ClientAuctionHandler implements ResponseHandler {

    private static final Logger log = LoggerFactory.getLogger(ClientAuctionHandler.class);

    // ── Public event constants ─────────────────────────────────────────────
    // Declared here so UI controllers import a single class for all auction events.

    /**
     * Fired when the server confirms an Auto-Bid has been registered, upgraded, or cancelled.
     * Payload: {@code Map<String,Object>} with keys {@code auctionId}, {@code maxBid},
     * {@code increment}, {@code isActive}.
     */
    public static final String AUTOBID_SETUP_SUCCESS = "AUTOBID_SETUP_SUCCESS";

    /**
     * Fired (broadcast to all watchers) when a bot is actively firing in an auction.
     * UI should display a visual indicator (e.g. green dot) to signal bot activity.
     * Payload: {@code Map<String,Object>} with key {@code auctionId}.
     */
    public static final String AUTOBID_ACTIVE = "AUTOBID_ACTIVE";

    // ─────────────────────────────────────────────────────────────────────────
    // ResponseHandler contract
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Dispatches the incoming network message to the appropriate EventBus event.
     *
     * <p>This method is called on a background (network) thread by
     * {@link client.handler.ResponseDispatcher}. Any UI mutation triggered by
     * the EventBus listeners <strong>must</strong> use {@code Platform.runLater(…)}.</p>
     *
     * @param message The network message from the server.
     * @param client  The active network client session (unused here but part of interface).
     */
    @Override
    public void handle(NetworkMessage message, NetworkClient client) {
        String command = message.getCommand();

        switch (command) {

            // ── Real-time price update (broadcast to all auction watchers) ──
            case "UPDATE_AUCTION_PRICE" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> priceData = (Map<String, Object>) message.getData();
                String auctionId = (String) priceData.get("auctionId");
                long newPrice = ((Number) priceData.get("newPrice")).longValue();
                log.info("[Auction {}] Price updated → {}", auctionId, newPrice);
                AuctionEventBus.fireEvent(AuctionEventBus.PRICE_UPDATED, priceData);
            }

            // ── Auto-Bid registration / update / cancel confirmed ───────────
            case "AUTOBID_SETUP_SUCCESS" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> autobidData = (Map<String, Object>) message.getData();
                boolean isActive = Boolean.TRUE.equals(autobidData.get("isActive"));
                log.info("[AutoBid] Setup confirmed — auctionId={}, active={}",
                        autobidData.get("auctionId"), isActive);
                // UI: BidPanelController listens to this event to toggle the button
                //     label, update the status label, and change the signal dot colour.
                AuctionEventBus.fireEvent(AUTOBID_SETUP_SUCCESS, autobidData);
            }

            // ── Bot is actively firing in this auction (broadcast) ──────────
            case "AUTOBID_ACTIVE" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> botData = (Map<String, Object>) message.getData();
                String auctionId = (String) botData.get("auctionId");
                log.info("[AutoBid] Bot active in auction {}", auctionId);
                // UI: BidPanelController changes the signal indicator to green.
                AuctionEventBus.fireEvent(AUTOBID_ACTIVE, botData);
            }

            // ── The user's own manual bid was accepted by the server ─────────
            case "BID_SUCCESS" -> {
                log.info("[Bid] Manual bid accepted: {}", message.getData());
                AuctionEventBus.fireEvent(AuctionEventBus.BID_SUCCESS, message.getData());
            }

            // ── An auction's lifecycle state changed (ENDED, CANCELLED, etc.) ─
            case "AUCTION_STATUS_CHANGED" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> statusData = (Map<String, Object>) message.getData();
                log.info("[Auction] Status changed → {}", statusData);
                AuctionEventBus.fireEvent("AUCTION_STATUS_CHANGED", statusData);
            }

            // ── Server-wide text broadcast ───────────────────────────────────
            case "CLI_BROADCAST" -> {
                log.info("[Broadcast] {}", message.getData());
                AuctionEventBus.fireEvent("CLI_BROADCAST", message.getData());
            }

            // ── New auction created (seller confirmation) ────────────────────
            case "CREATE_SUCCESS" -> {
                log.info("[Auction] Created: {}", message.getData());
                AuctionEventBus.fireEvent(AuctionEventBus.AUCTION_CREATED, message.getData());
            }

            // ── In-auction chat message ──────────────────────────────────────
            case "CHAT" -> {
                log.debug("[Chat] {}", message.getData());
                AuctionEventBus.fireEvent("CHAT", message.getData());
            }

            default -> log.warn("[ClientAuctionHandler] Unrecognised command: {}", command);
        }
    }
}