package server.handler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.user.User;
import network.NetworkMessage;
import server.ClientHandler;
import utils.JacksonConfig;

import static utils.ConsoleColors.*;

/**
 * Handles authentication-related commands including login, registration, and logout.
 * This handler manages the transition of a connection from an anonymous guest
 * to an authenticated user and coordinates with the {@link controller.UserController}
 * to verify credentials and persist new accounts.
 */
public class AuthHandler implements CommandHandler {

    /** Jackson object mapper used to convert generic network data into domain models. */
    private final ObjectMapper mapper = JacksonConfig.mapper();

    /**
     * Dispatches authentication commands to their respective processing methods.
     *
     * @param message The network message containing the command (LOGIN, REGISTER, LOGOUT).
     * @param client  The client handler representing the active socket connection.
     */
    @Override
    public void handle(NetworkMessage message, ClientHandler client) {
        String command = message.getCommand();

        if ("LOGIN".equals(command)) {
            processLogin(message.getData(), client);
        } else if ("REGISTER".equals(command)) {
            processRegister(message.getData(), client);
        } else if ("LOGOUT".equals(command)) {
            processLogout(client);
        }
    }

    /**
     * Clears the user session data from the active ClientHandler socket
     * and reverts the connection identity back to an anonymous guest.
     * This effectively signs the user out of the system while keeping the connection alive.
     *
     * @param client The client handler to be reset.
     */
    private void processLogout(ClientHandler client) {
        String oldName = client.getClientName();
        client.setUser(null);
        client.setClientName("#Guest"+ClientHandler.getcNC());
        ClientHandler.incrementcNC();

        System.out.println("[System]: \"" + YELLOW + oldName + RESET + "\" signed out and reverted to " + client.getClientName());
    }

    /**
     * Processes a login attempt by converting raw network data into a User model
     * and verifying the credentials via the controller.
     * If successful, the client's identity is updated to the authenticated user.
     *
     * @param data   The data payload containing username and password.
     * @param client The client handler requesting the login.
     */
    private void processLogin(Object data, ClientHandler client) {
        try {
            // Convert the generic data object into a User model for easier handling
            User loginAttempt = mapper.convertValue(data, User.class);
            User user = client.getUserController().login(loginAttempt.getUserName(), loginAttempt.getUserPass());

            if (user != null) {
                client.setClientName(user.getUserName());
                client.setUser(user);
                client.sendResponse("LOGIN_SUCCESS", user);
            } else {
                client.sendResponse("LOGIN_FAIL", "Wrong username or password");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("[Error]: Mapping JSON to User (Login): " + RED + e.getMessage() + RESET);
            client.sendResponse("ERROR", "Invalid login data");
        }
    }

    /**
     * Processes a registration attempt.
     * Handles account creation logic and extracts 2FA (TOTP) details if the
     * registration is successful.
     *
     * @param data   The data payload containing new account information.
     * @param client The client handler requesting registration.
     */
    private void processRegister(Object data, ClientHandler client) {
        try {
            User regUser = mapper.convertValue(data, User.class);
            String result = client.getUserController().register(
                    regUser.getUserName(),
                    regUser.getUserPass(),
                    regUser.getName(),
                    regUser.getRole()
            );

            if (result != null && result.startsWith("SUCCESS|")) {
                // Parse the 2FA secret and QR URL from the success string
                String[] parts = result.split("\\|");
                String secretKey = parts[1];
                String qrUrl = parts[2];
                String[] responseData = {secretKey, qrUrl};

                client.sendResponse("REGISTER_SUCCESS", responseData);
                client.setClientName(regUser.getUserName());
            } else {
                client.sendResponse("REGISTER_FAIL", result);
            }
        } catch (IllegalArgumentException e) {
            System.out.println("[Error]: Mapping JSON to User (Register): " + RED + e.getMessage() + RESET);
            client.sendResponse("ERROR", "Invalid register data");
        }
    }
}