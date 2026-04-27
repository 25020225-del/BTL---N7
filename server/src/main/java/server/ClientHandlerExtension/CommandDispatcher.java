package server.ClientHandlerExtension;

import network.NetworkMessage;
import server.ClientHandler;

import java.util.HashMap;
import java.util.Map;

import static utils.ConsoleColors.*;

public class CommandDispatcher {
    private final Map<String, CommandHandler> handlers = new HashMap<>();

    public CommandDispatcher() {
        registerHandlers();
    }

    private void registerHandlers() {
        AuthHandler authHandler = new AuthHandler();
        handlers.put("LOGIN", authHandler);
        handlers.put("REGISTER", authHandler);

        AuctionActionHandler auctionHandler = new AuctionActionHandler();
        handlers.put("CREATE_AUCTION", auctionHandler);

        PaymentHandler paymentHandler = new PaymentHandler();
        handlers.put("CREATE_DEPOSIT", paymentHandler);
        handlers.put("CONFIRM_DEPOSIT", paymentHandler);

        handlers.put("PING", (message, client) -> client.sendResponse("PONG", "Request accepted"));
    }

    public void dispatch(NetworkMessage message, ClientHandler client) {
        String command = message.getCommand();
        CommandHandler handler = handlers.get(command);

        if (handler != null) {
            try {
                handler.handle(message, client);
            } catch (Exception e) {
                System.out.println("[Error]: Error executing command " + command + ": " + RED + e.getMessage() + RESET);
                client.sendResponse("ERROR", "Internal server error while processing command");
            }
        } else {
            System.out.println("[Error]: Unrecognized command: " + RED + command + RESET);
            client.sendResponse("ERROR", "Unrecognized command");
        }
    }
}