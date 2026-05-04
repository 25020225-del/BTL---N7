package server.handler;

import network.NetworkMessage;
import server.ClientHandler;

import java.util.HashMap;
import java.util.Map;

import static utils.ConsoleColors.RED;
import static utils.ConsoleColors.RESET;

/**
 * The central command router for the server application.
 * This class implements the Command Pattern by maintaining a registry of {@link CommandHandler}
 * implementations. It decodes incoming {@link NetworkMessage} objects and dispatches them
 * to the appropriate handler based on the command string.
 */
public class CommandDispatcher {

    /**
     * A registry mapping unique command strings (e.g., "LOGIN", "CREATE_AUCTION")
     * to their respective operational handlers.
     */
    private final Map<String, CommandHandler> handlers = new HashMap<>();

    /**
     * Constructs a new CommandDispatcher and initializes the handler registry.
     */
    public CommandDispatcher() {
        registerHandlers();
    }

    /**
     * Registers all available command handlers into the dispatcher.
     * Handlers are grouped by functional domains:
     * <ul>
     *     <li><b>System:</b> Connectivity and synchronization.</li>
     *     <li><b>Auth:</b> Identity and access management.</li>
     *     <li><b>Auction:</b> Core marketplace operations.</li>
     *     <li><b>Payment:</b> Financial transactions and wallet management.</li>
     *     <li><b>Fetch:</b> Data retrieval for clients.</li>
     *     <li><b>Admin:</b> Privileged system oversight.</li>
     * </ul>
     */
    private void registerHandlers() {
        // Register System level functions (Ping, Time Sync)
        SystemHandler sysHandler = new SystemHandler();
        handlers.put("PING", sysHandler);
        handlers.put("TIME_SYNC", sysHandler);

        // Register Authentication and Session management
        AuthHandler authHandler = new AuthHandler();
        handlers.put("LOGIN", authHandler);
        handlers.put("REGISTER", authHandler);
        handlers.put("LOGOUT", authHandler);

        // Register Auction creation and management
        AuctionActionHandler auctionHandler = new AuctionActionHandler();
        handlers.put("CREATE_AUCTION", auctionHandler);

        // Register Financial/Payment processing
        PaymentHandler paymentHandler = new PaymentHandler();
        handlers.put("CREATE_DEPOSIT", paymentHandler);
        handlers.put("CONFIRM_DEPOSIT", paymentHandler);

        // Register Data Fetching operations
        FetchAuctionsHandler fetchHandler = new FetchAuctionsHandler();
        handlers.put("FETCH_AUCTIONS", fetchHandler);
        handlers.put("FETCH_PENDING_AUCTIONS", fetchHandler);

        // Register Administrative operations
        AdminActionHandler adminHandler = new AdminActionHandler();
        handlers.put("APPROVE_AUCTION", adminHandler);
        handlers.put("REJECT_AUCTION", adminHandler);
    }

    /**
     * Routes an incoming network message to its associated handler.
     * If the command is recognized, the handler's logic is executed; otherwise,
     * an error response is sent back to the client.
     *
     * @param message The incoming network message containing the command and data.
     * @param client  The client handler session that sent the message.
     */
    public void dispatch(NetworkMessage message, ClientHandler client) {
        String command = message.getCommand();
        CommandHandler handler = handlers.get(command);

        if (handler != null) {
            try {
                // Execute the handler logic
                handler.handle(message, client);
            } catch (Exception e) {
                // Global error handling for unexpected runtime failures during command execution
                System.out.println("[Error]: Error executing command " + command + ": " + RED + e.getMessage() + RESET);
                client.sendResponse("ERROR", "Internal server error while processing command");
            }
        } else {
            // Log and notify client of invalid/unsupported commands
            System.out.println("[Error]: Unrecognized command: " + RED + command + RESET);
            client.sendResponse("ERROR", "Unrecognized command");
        }
    }
}