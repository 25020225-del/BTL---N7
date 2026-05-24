package server.handler;

import network.NetworkMessage;
import server.ClientHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Command route handler managing baseline, non-business infrastructure packets
 * such as diagnostic heartbeats and distributed server clock synchronization.
 */
public class SystemHandler implements CommandHandler {

    @Override
    public void handle(NetworkMessage message, ClientHandler client) {
        String command = message.getCommand();

        if ("PING".equals(command)) {
            client.sendResponse("PONG", "Request accepted");

        } else if ("TIME_SYNC".equals(command)) {
            long clientSendTime = ((Number) message.getData()).longValue();
            long serverTime = System.currentTimeMillis();

            Map<String, Long> responseData = new HashMap<>();
            responseData.put("clientSendTime", clientSendTime);
            responseData.put("serverTime", serverTime);

            client.sendResponse("TIME_SYNC_ACK", responseData);
        }
    }
}