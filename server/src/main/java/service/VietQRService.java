package service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service responsible for generating VietQR payment strings.
 * Adheres to the Single Responsibility Principle by isolating formatting logic.
 */
public class VietQRService {
    private static final Logger log = LoggerFactory.getLogger(VietQRService.class);

    private static final String BANK_BIN = "970422";
    private static final String ACCOUNT_NUMBER = "0815567462";

    /**
     * Generates a Deep Link URL that banking apps can scan to auto-fill payment details.
     * This avoids the need to manually compute the strict EMVCo CRC16 checksum.
     *
     * @param amount  The exact deposit amount in VND.
     * @param orderId The unique transaction identifier used for the memo.
     * @return A scannable string payload for ZXing to render.
     */
    public String generateVietQRString(long amount, String orderId) {
        String memo = "N7 " + orderId;
        log.info("Generating VietQR payload for Order: {}, Amount: {}", orderId, amount);

        // VietQR Quick Link format
        return String.format("https://dl.vietqr.io/pay?app=napas247&bin=%s&acc=%s&amount=%d&memo=%s",
                BANK_BIN, ACCOUNT_NUMBER, amount, memo.replace(" ", "%20"));
    }
}