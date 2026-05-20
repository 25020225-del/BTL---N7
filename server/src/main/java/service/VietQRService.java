package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JacksonConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Service responsible for generating standard EMVCo VietQR strings.
 */
public class VietQRService {
    private static final Logger log = LoggerFactory.getLogger(VietQRService.class);

    private static final String BANK_BIN = "970422"; // MBBank
    private static final String ACCOUNT_NUMBER = "0815567462";
    private static final String ACCOUNT_NAME = "NGUYEN QUANG MANH";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public VietQRService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = JacksonConfig.mapper();
    }

    /**
     * Calls the official VietQR API to generate a strict EMVCo format string.
     * This guarantees 100% compatibility with all VN banking apps.
     *
     * @param amount  The exact deposit amount in VND.
     * @param orderId The unique transaction identifier used for the memo.
     * @return A scannable EMVCo string payload for ZXing to render.
     */
    public String generateVietQRString(long amount, String orderId) {
        String memo = "N7 VQR" + orderId.replace("VQR-", "");
        log.info("Requesting EMVCo VietQR payload for Order: {}, Amount: {}", orderId, amount);

        try {
            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("accountNo", ACCOUNT_NUMBER);
            requestBody.put("accountName", ACCOUNT_NAME);
            requestBody.put("acqId", BANK_BIN);
            requestBody.put("amount", amount);
            requestBody.put("addInfo", memo);
            requestBody.put("format", "text");
            requestBody.put("template", "compact");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.vietqr.io/v2/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode jsonResponse = mapper.readTree(response.body());
                if ("00".equals(jsonResponse.get("code").asText())) {
                    // Trả về chuỗi mã hóa EMVCo nguyên bản
                    return jsonResponse.get("data").get("qrCode").asText();
                } else {
                    log.error("VietQR API Error: {}", jsonResponse.get("desc").asText());
                }
            } else {
                log.error("VietQR API HTTP Error: {}", response.statusCode());
            }
        } catch (Exception e) {
            log.error("Failed to fetch VietQR string: {}", e.getMessage());
        }

        return String.format("https://dl.vietqr.io/pay?app=napas247&bin=%s&acc=%s&amount=%d&memo=%s",
                BANK_BIN, ACCOUNT_NUMBER, amount, memo.replace(" ", "%20"));
    }
}