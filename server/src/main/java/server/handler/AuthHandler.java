package server.handler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.User;
import network.NetworkMessage;
import server.ClientHandler;

import static utils.ConsoleColors.*;

public class AuthHandler implements CommandHandler {
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public void handle(NetworkMessage message, ClientHandler client) {
        String command = message.getCommand();

        if ("LOGIN".equals(command)) {
            processLogin(message.getData(), client);
        } else if ("REGISTER".equals(command)) {
            processRegister(message.getData(), client);
        }
    }

    private void processLogin(Object data, ClientHandler client) {
        try {
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