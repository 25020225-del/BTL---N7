package server.handler;

import controller.ServerBidderController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);

    private final Map<String, CommandHandler> handlers = new HashMap<>();

    // Dependencies for DI
    private final database.dao.UserDAO userDAO;
    private final database.dao.AuctionDAO auctionDAO;
    private final database.dao.BidDAO bidDAO;
    private final database.dao.WalletDAO walletDAO;
    private final service.TOTPService totpService;
    private final controller.ServerSellerController sellerCtrl;
    private final controller.ServerPaymentController paymentCtrl;

    /**
     * Constructs a new CommandDispatcher and initializes the handler registry with dependencies.
     */
    public CommandDispatcher(
            database.dao.UserDAO userDAO,
            database.dao.AuctionDAO auctionDAO,
            database.dao.BidDAO bidDAO,
            database.dao.WalletDAO walletDAO,
            service.TOTPService totpService,
            controller.ServerSellerController sellerCtrl,
            controller.ServerPaymentController paymentCtrl) {
        
        this.userDAO = userDAO;
        this.auctionDAO = auctionDAO;
        this.bidDAO = bidDAO;
        this.walletDAO = walletDAO;
        this.totpService = totpService;
        this.sellerCtrl = sellerCtrl;
        this.paymentCtrl = paymentCtrl;
        
        registerHandlers();
    }

    /**
     * Registers all available command handlers into the dispatcher.
     */
    private void registerHandlers() {
        // Register System level functions (Ping, Time Sync)
        SystemHandler sysHandler = new SystemHandler();
        handlers.put("PING", sysHandler);
        handlers.put("TIME_SYNC", sysHandler);

        // Register Authentication and Session management
        AuthHandler authHandler = new AuthHandler(); // AuthHandler uses client.getUserController()
        handlers.put("LOGIN", authHandler);
        handlers.put("REGISTER", authHandler);
        handlers.put("LOGOUT", authHandler);

        // Register Auction creation and management
        AuctionActionHandler auctionHandler = new AuctionActionHandler(sellerCtrl);
        handlers.put("CREATE_AUCTION", auctionHandler);

        // Register Financial/Payment processing
        PaymentHandler paymentHandler = new PaymentHandler(paymentCtrl);
        handlers.put("CREATE_DEPOSIT", paymentHandler);
        handlers.put("CONFIRM_DEPOSIT", paymentHandler);

        // Register Data Fetching operations
        FetchAuctionsHandler fetchHandler = new FetchAuctionsHandler(auctionDAO);
        handlers.put("FETCH_AUCTIONS", fetchHandler);

        // Register Admin operations
        AdminActionHandler adminHandler = new AdminActionHandler(auctionDAO, userDAO);
        handlers.put("FETCH_PENDING_AUCTIONS", new FetchAuctionsHandler(auctionDAO));
        handlers.put("APPROVE_AUCTION", adminHandler);
        handlers.put("REJECT_AUCTION", adminHandler);
        handlers.put("FETCH_USERS", adminHandler);
        handlers.put("BLOCK_USER", adminHandler);
        handlers.put("UNBLOCK_USER", adminHandler);
        
        // Register Bidding operations
        BidActionHandler bidHandler = new BidActionHandler(new controller.ServerBidderController(bidDAO), auctionDAO);
        handlers.put("PLACE_BID", bidHandler);
        handlers.put("SETUP_AUTOBID", bidHandler);

        // Register Seller operations
        SellerActionHandler sellerHandler = new SellerActionHandler(sellerCtrl, auctionDAO);
        handlers.put("EDIT_AUCTION", sellerHandler);
        handlers.put("DELETE_AUCTION", sellerHandler);
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
                log.error("Error executing command {}: {}", command, e.getMessage());
                client.sendResponse("ERROR", "Internal server error while processing command");
            }
        } else {
            // Log and notify client of invalid/unsupported commands
            log.warn("Unrecognized command: {}", command);
            client.sendResponse("ERROR", "Unrecognized command");
        }
    }
}