package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JacksonConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Integration service managing financial order lifecycles via PayPal REST APIs.
 * Handles server-to-server OAuth2 authentication, intent registration, and payment captures.
 */
public class PayPalService {

    private static final Logger log = LoggerFactory.getLogger(PayPalService.class);
    private final String clientId;
    private final String secret;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public PayPalService() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        this.clientId = dotenv.get("PAYPAL_CLIENT_ID");
        this.secret = dotenv.get("PAYPAL_SECRET");
        this.baseUrl = dotenv.get("PAYPAL_BASE_URL", "https://api-m.sandbox.paypal.com");

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = JacksonConfig.mapper();
    }

    private String getAccessToken() throws Exception {
        String auth = clientId + ":" + secret;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/oauth2/token"))
                .header("Authorization", "Basic " + encodedAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("PayPal authentication error: " + response.body());
        }

        JsonNode jsonNode = mapper.readTree(response.body());
        return jsonNode.get("access_token").asText();
    }

    /**
     * Initializes a transactional payment intent order based on a specified fiat currency bounds.
     *
     * @param amountVND baseline volume quantity evaluated under Vietnamese Dong metrics
     * @return a composite array containing the generated Order ID at index 0 and approval Href link at index 1
     * @throws Exception if serialization or remote socket processing drops
     */
    public String[] createOrder(long amountVND) throws Exception {
        double amountUSD = ExchangeRateService.getInstance().vndToUsd(amountVND);
        double currentRate = ExchangeRateService.getInstance().getUsdToVndRate();
        log.info("Creating order: {} VND → {} USD (rate: {})",
                amountVND,
                String.format(java.util.Locale.US, "%.2f", amountUSD),
                String.format(java.util.Locale.US, "%.2f", currentRate));
        String token = getAccessToken();

        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("intent", "CAPTURE");

        ArrayNode purchaseUnits = requestBody.putArray("purchase_units");
        ObjectNode amountNode = mapper.createObjectNode();
        ObjectNode currencyNode = mapper.createObjectNode();

        currencyNode.put("currency_code", "USD");
        currencyNode.put("value", String.format(java.util.Locale.US, "%.2f", amountUSD));
        amountNode.set("amount", currencyNode);
        purchaseUnits.add(amountNode);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v2/checkout/orders"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new RuntimeException("Unable to create a PayPal order: " + response.body());
        }

        JsonNode jsonResponse = mapper.readTree(response.body());
        String orderId = jsonResponse.get("id").asText();
        String approvalLink = "";

        ArrayNode links = (ArrayNode) jsonResponse.get("links");
        for (JsonNode link : links) {
            if ("approve".equals(link.get("rel").asText())) {
                approvalLink = link.get("href").asText();
                break;
            }
        }

        return new String[]{orderId, approvalLink};
    }

    /**
     * Finalizes and securely locks funds for an authorized transaction block sequence.
     *
     * @param orderId the tracking intent token code verified by the client browser redirect
     * @return true if status changes successfully transit into a COMPLETED state
     * @throws Exception on platform communication error states
     */
    public boolean captureOrder(String orderId) throws Exception {
        String token = getAccessToken();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v2/checkout/orders/" + orderId + "/capture"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 201 || response.statusCode() == 200) {
            JsonNode jsonResponse = mapper.readTree(response.body());
            String status = jsonResponse.get("status").asText();
            return "COMPLETED".equals(status);
        }

        log.warn("PayPal Capture failed: {}", response.statusCode());
        return false;
    }

    /**
     * Extracts processing status markers tied to an active order reference tracking token.
     *
     * @param orderId active identifier token code targeted for verification queries
     * @return state descriptor string expressions (e.g., CREATED, APPROVED, COMPLETED)
     * @throws Exception if network connections drop
     */
    public String getOrderStatus(String orderId) throws Exception {
        String token = getAccessToken();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v2/checkout/orders/" + orderId))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode jsonResponse = mapper.readTree(response.body());
            return jsonResponse.get("status").asText();
        }
        return "UNKNOWN";
    }

    /**
     * Inspects target execution manifests to recalculate and back-convert confirmed currency values into VND.
     *
     * @param orderId authenticated identifier token code requiring ledger audit inspections
     * @return total evaluated currency volume converted back to baseline integer metrics
     * @throws Exception if JSON schema mismatches intercept parsing loops
     */
    public long getCapturedAmountVND(String orderId) throws Exception {
        String token = getAccessToken();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v2/checkout/orders/" + orderId))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode jsonResponse = mapper.readTree(response.body());
            JsonNode purchaseUnits = jsonResponse.get("purchase_units");

            if (purchaseUnits != null && purchaseUnits.isArray() && !purchaseUnits.isEmpty()) {
                String valueStr = purchaseUnits.get(0).get("amount").get("value").asText();
                double amountUSD = Double.parseDouble(valueStr);
                long amountVND = ExchangeRateService.getInstance().usdToVnd(amountUSD);
                log.info("Captured: {} USD -> {} VND (live rate: {})",
                        amountUSD, amountVND, ExchangeRateService.getInstance().getUsdToVndRate());
                return amountVND;
            }
        }

        log.warn("Failed to verify the captured amount for Order ID: {}. HTTP Status: {}", orderId, response.statusCode());
        return 0L;
    }

    /**
     * Executes an automated money transfer via PayPal Payouts API.
     * 
     * @param userEmail target PayPal account email to send funds to
     * @param amountVND target numerical VND amount to be converted and paid
     * @return true if the payout transaction was successfully registered on PayPal
     * @throws Exception if network or authentication error occurs
     */
    public boolean executePayPalPayout(String userEmail, long amountVND) throws Exception {
        double amountUSD = ExchangeRateService.getInstance().vndToUsd(amountVND);
        String token = getAccessToken();

        ObjectNode requestBody = mapper.createObjectNode();
        ObjectNode senderHeader = requestBody.putObject("sender_batch_header");
        senderHeader.put("sender_batch_id", "Payout-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0, 4));
        senderHeader.put("email_subject", "You received a withdrawal from N7 Auction System!");

        ArrayNode items = requestBody.putArray("items");
        ObjectNode item = mapper.createObjectNode();
        item.put("recipient_type", "EMAIL");
        item.put("receiver", userEmail);
        item.put("note", "Automated withdrawal settlement transaction.");
        
        ObjectNode amountNode = item.putObject("amount");
        amountNode.put("value", String.format(java.util.Locale.US, "%.2f", amountUSD));
        amountNode.put("currency", "USD");

        items.add(item);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/payments/payouts"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 201 || response.statusCode() == 200) {
            log.info("PayPal Payout created successfully for {} ({} VND / {} USD)", userEmail, amountVND, amountUSD);
            return true;
        }

        log.error("PayPal Payout failed with status {}: {}", response.statusCode(), response.body());
        return false;
    }
}