package server.handler;

import network.NetworkMessage;
import server.ClientHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles foundational system-level commands that are not tied
 * to business logic (e.g., PING checks, Network Time Synchronization).
 */
public class SystemHandler implements CommandHandler {
    @Override
    public void handle(NetworkMessage message, ClientHandler client) {
        String command = message.getCommand();

        if ("PING".equals(command)) {
            // Heartbeat check response
            client.sendResponse("PONG", "Request accepted");

        } else if ("TIME_SYNC".equals(command)) {
            // Process Network Time Protocol (NTP) style synchronization request
            long clientSendTime = ((Number) message.getData()).longValue();
            long serverTime = System.currentTimeMillis();

            Map<String, Long> responseData = new HashMap<>();
            responseData.put("clientSendTime", clientSendTime);
            responseData.put("serverTime", serverTime);

            client.sendResponse("TIME_SYNC_ACK", responseData);
        }
    }
}