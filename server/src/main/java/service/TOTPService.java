package service;

import com.warrenstrange.googleauth.GoogleAuthenticator;

import java.net.URLEncoder;

import static utils.ConsoleColors.RED;
import static utils.ConsoleColors.RESET;

/**
 * Service responsible for managing Time-based One-Time Password (TOTP) security.
 * It provides functionality for generating unique secret keys, constructing
 * QR code URLs for authenticator apps (like Google Authenticator), and
 * verifying time-sensitive tokens.
 */
public class TOTPService {

    /**
     * Internal library instance used for core TOTP logic and key generation.
     */
    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    /**
     * Generates a new Base32 encoded secret key.
     * This key should be stored securely in the database and shared with the user
     * during the 2FA setup process.
     *
     * @return A randomly generated secret key string.
     */
    public String createSecretKey() {
        return gAuth.createCredentials().getKey();
    }

    /**
     * Constructs a standard {@code otpauth://} URI used to generate QR codes.
     * This URI follows the Google Authenticator Key URI format, allowing users
     * to easily scan and add the account to their 2FA application.
     *
     * @param username  The name of the user account to be displayed in the app.
     * @param secretKey The user's unique secret key.
     * @return A formatted OTP authentication URI.
     */
    public String getQRUrl(String username, String secretKey) {
        try {
            String issuer = "AuctionSystem-N7";
            // Ensure the username is properly URL-encoded to handle special characters
            String encodedUsername = URLEncoder.encode(username, "UTF-8").replace("+", "%20");
            return "otpauth://totp/" + issuer + ":" + encodedUsername + "?secret=" + secretKey + "&issuer=" + issuer;
        } catch (Exception e) {
            System.out.println("[Error]: URL Encoding failed: " + RED + e.getMessage() + RESET);
            e.printStackTrace();
            // Fallback to a basic URI if encoding fails
            return "otpauth://totp/AuctionSystem-N7:" + username + "?secret=" + secretKey + "&issuer=AuctionSystem-N7";
        }
    }

    /**
     * Verifies a 6-digit TOTP code provided by the user against their secret key.
     * The verification accounts for time drift based on the default window of the library.
     *
     * @param secretKey The secret key associated with the user account.
     * @param code      The 6-digit integer code provided by the user.
     * @return {@code true} if the code is valid for the current time window; {@code false} otherwise.
     */
    public boolean verifyCode(String secretKey, int code) {
        return gAuth.authorize(secretKey, code);
    }
}