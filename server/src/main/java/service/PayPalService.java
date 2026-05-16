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

        //Set httpClient with timeout to avoid server thread suspension when there's network error
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = JacksonConfig.mapper();
        ;
    }

    /**
     * OAuth2 Authentication Protocol: Exchange the Client ID and Secret for a one-time Access Token.
     */
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
     * Create a new trade (Order) based on the amount in VND
     *
     * @return String[]: [0] = Order ID, [1] = Payment URL.
     */
    public String[] createOrder(long amountVND) throws Exception {
        // Assumed exchange rate | TODO: add dynamic foreign exchange rate API)
        double amountUSD = amountVND / 25000.0;
        String token = getAccessToken();

        // Create a complex JSON structure for a PayPal order using Jackson
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

        // Browse through the list of links to find URLs with "rel" = "approve"
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
     * Confirm completion and process payment (Capture) after the client confirms payment in the browser.
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
     * Lấy trạng thái hiện tại của Order từ PayPal.
     * Trạng thái trả về thường là: "CREATED", "APPROVED" (đã đồng ý trên web), "COMPLETED".
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
     * Queries the PayPal API to retrieve the actual captured amount and converts it to VND.
     *
     * @param orderId The PayPal Order ID to verify.
     * @return The actual captured amount in VND, or 0 if verification fails.
     * @throws Exception If a network or JSON parsing error occurs.
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

            // Extract the amount from the PayPal JSON payload
            if (purchaseUnits != null && purchaseUnits.isArray() && !purchaseUnits.isEmpty()) {
                String valueStr = purchaseUnits.get(0).get("amount").get("value").asText();
                double amountUSD = Double.parseDouble(valueStr);

                // Convert back to VND (Using the same 25000.0 exchange rate as createOrder)
                return (long) (amountUSD * 25000.0);
            }
        }

        log.warn("Failed to verify the captured amount for Order ID: {}. HTTP Status: {}", orderId, response.statusCode());
        return 0L;
    }
}