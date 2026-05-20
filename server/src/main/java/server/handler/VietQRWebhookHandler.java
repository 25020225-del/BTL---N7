package server.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import controller.ServerPaymentController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JacksonConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles incoming HTTP Webhook requests from Bank APIs (e.g., Casso, SePay).
 * Parses the transaction memo using Regex to reconcile direct bank transfers.
 */
public class VietQRWebhookHandler implements HttpHandler {
    private static final Logger log = LoggerFactory.getLogger(VietQRWebhookHandler.class);
    private final ObjectMapper mapper = JacksonConfig.mapper();
    private final ServerPaymentController paymentController;

    public VietQRWebhookHandler(ServerPaymentController paymentController) {
        this.paymentController = paymentController;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendHttpResponse(exchange, 405, "{\"success\": false, \"message\": \"Method Not Allowed\"}");
            return;
        }

        try (InputStream is = exchange.getRequestBody()) {
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            log.info("Received Bank Webhook payload: {}", requestBody);

            JsonNode payload = mapper.readTree(requestBody);
            JsonNode dataArray = payload.path("data");

            if (dataArray.isArray() && dataArray.size() > 0) {
                for (JsonNode transaction : dataArray) {
                    long receivedAmount = transaction.path("amount").asLong();
                    String description = transaction.path("description").asText();

                    Pattern pattern = Pattern.compile("(VQR-\\d+)");
                    Matcher matcher = pattern.matcher(description);

                    if (matcher.find()) {
                        String orderId = matcher.group(1);
                        log.info("Extracted Order ID: {} with Amount: {}", orderId, receivedAmount);

                        // Find pending deposits in RAM
                        PaymentHandler.DepositInfo info = PaymentHandler.pendingDeposits.get(orderId);

                        if (info != null) {
                            // Reconciliation
                            if (info.getAmountVND() == receivedAmount) {
                                // Prevent Double-spending
                                if (info.getIsProcessing().compareAndSet(false, true)) {
                                    paymentController.processDepositSuccess(info.getUser(), orderId, receivedAmount)
                                            .thenAccept(success -> {
                                                if (success) {
                                                    PaymentHandler.pendingDeposits.remove(orderId);
                                                    info.getClient().sendResponse("DEPOSIT_SUCCESS",
                                                            "VietQR payment successful. Your balance has been updated.");
                                                    log.info("Reconciliation successful for Order: {}", orderId);
                                                } else {
                                                    info.getIsProcessing().set(false);
                                                }
                                            });
                                }
                            } else {
                                log.warn("Amount mismatch for Order {}. Expected: {}, Received: {}",
                                        orderId, info.getAmountVND(), receivedAmount);
                            }
                        } else {
                            log.warn("Order code {} not found in pending deposits (Expired or Invalid).", orderId);
                        }
                    }
                }
            }

            sendHttpResponse(exchange, 200, "{\"success\": true}");

        } catch (Exception e) {
            log.error("Failed to process Bank Webhook", e);
            sendHttpResponse(exchange, 500, "{\"success\": false, \"message\": \"Internal Server Error\"}");
        }
    }

    private void sendHttpResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}