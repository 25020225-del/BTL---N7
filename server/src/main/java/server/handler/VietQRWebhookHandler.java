package server.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import controller.ServerPaymentController;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JacksonConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Handles incoming HTTP Webhook requests from the VietQR payment gateway (e.g., PayOS, Casso).
 * Applies the Single Responsibility Principle by strictly managing HTTP parsing and validation.
 */
public class VietQRWebhookHandler implements HttpHandler {
    private static final Logger log = LoggerFactory.getLogger(VietQRWebhookHandler.class);

    // Reuse the centralized Jackson ObjectMapper [cite: 1901, 1902, 1903, 1904, 1905, 1906]
    private final ObjectMapper mapper = JacksonConfig.mapper();
    private final ServerPaymentController paymentController;

    /**
     * Dependency Injection for the payment controller.
     * * @param paymentController Controller handling the core financial business logic.
     */
    public VietQRWebhookHandler(ServerPaymentController paymentController) {
        this.paymentController = paymentController;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // 1. Guard Clause: Only accept POST requests for Webhooks
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendHttpResponse(exchange, 405, "{\"success\": false, \"message\": \"Method Not Allowed\"}");
            return;
        }

        try (InputStream is = exchange.getRequestBody()) {
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            log.info("Received VietQR Webhook payload: {}", requestBody);

            // TODO: Verify Signature/HMAC here to ensure data integrity and prevent spoofing.
            // if (!isValidSignature(exchange, requestBody)) {
            //     sendHttpResponse(exchange, 401, "{\"success\": false, \"message\": \"Unauthorized\"}");
            //     return;
            // }

            // 2. Parse JSON payload
            JsonNode payload = mapper.readTree(requestBody);

            // Assuming standard format: { "data": { "orderCode": "12345", "amount": 50000 } }
            JsonNode data = payload.path("data");
            String orderCode = data.path("orderCode").asText();
            long amount = data.path("amount").asLong();

            // 3. Retrieve pending transaction context (User)
            // TODO: Fetch the associated User from the database or a shared ConcurrentHashMap using the orderCode.
            User user = null; // fetchUserByOrderCode(orderCode);

            if (user != null) {
                // 4. Delegate business logic to Controller (Dependency Inversion)
                // Use the existing ACID transaction method[cite: 2204, 2205].
                paymentController.processDepositSuccess(user, "VQR-" + orderCode, amount)
                        .thenAccept(success -> {
                            if (success) {
                                log.info("Webhook processed successfully for Order: {}", orderCode);
                            }
                        });
            } else {
                log.warn("Order code {} not found in pending transactions.", orderCode);
            }

            // 5. Acknowledge receipt to the payment gateway
            sendHttpResponse(exchange, 200, "{\"success\": true}");

        } catch (Exception e) {
            log.error("Failed to process Webhook", e);
            sendHttpResponse(exchange, 500, "{\"success\": false, \"message\": \"Internal Server Error\"}");
        }
    }

    /**
     * Utility method to format and send HTTP responses.
     */
    private void sendHttpResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}