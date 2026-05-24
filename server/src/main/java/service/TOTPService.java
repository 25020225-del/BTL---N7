package service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;

/**
 * Core security service layer managing Time-based One-Time Password (TOTP) protocols.
 * Generates cryptographic key secrets, builds application configurations, and validates tokens.
 */
public class TOTPService {

    private static final Logger log = LoggerFactory.getLogger(TOTPService.class);
    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    /**
     * Generates an isolated cryptographically secure Base32 encoded identity secret key.
     *
     * @return randomized alphanumeric secret key credentials
     */
    public String createSecretKey() {
        return gAuth.createCredentials().getKey();
    }

    /**
     * Constructs a compliant otpauth URI scheme block required for dynamic QR app profile imports.
     *
     * @param username  alphanumeric login handle context descriptor
     * @param secretKey confirmed identity secure secret key reference
     * @return fully compiled canonical identification schema string
     */
    public String getQRUrl(String username, String secretKey) {
        try {
            String issuer = "AuctionSystem-N7";
            String encodedUsername = URLEncoder.encode(username, "UTF-8").replace("+", "%20");
            return "otpauth://totp/" + issuer + ":" + encodedUsername + "?secret=" + secretKey + "&issuer=" + issuer;
        } catch (Exception e) {
            log.warn("URL Encoding failed: {}", e.getMessage());
            return "otpauth://totp/AuctionSystem-N7:" + username + "?secret=" + secretKey + "&issuer=AuctionSystem-N7";
        }
    }

    /**
     * Evaluates a provided 6-digit numeric token against active time window metrics.
     *
     * @param secretKey account security holding secret key reference
     * @param code      the 6-digit structural verification code requested for assertion
     * @return true if parameters successfully pass standard drift tolerance checks
     */
    public boolean verifyCode(String secretKey, int code) {
        return gAuth.authorize(secretKey, code);
    }
}