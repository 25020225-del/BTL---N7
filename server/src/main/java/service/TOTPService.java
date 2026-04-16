package service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import java.net.URLEncoder;

public class TOTPService {

    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    public String createSecretKey() {
        final GoogleAuthenticatorKey key = gAuth.createCredentials();
        return key.getKey();
    }

    public String getQRUrl(String username, String secretKey) {
        try {
            String issuer = "AuctionSystem-N7";
            String encodedUsername = URLEncoder.encode(username, "UTF-8").replace("+", "%20");

            return "otpauth://totp/" + issuer + ":" + encodedUsername + "?secret=" + secretKey + "&issuer=" + issuer;
        } catch (Exception e) {
            e.printStackTrace();
            return "otpauth://totp/AuctionSystem-N7:" + username + "?secret=" + secretKey + "&issuer=AuctionSystem-N7";
        }
    }

    public boolean verifyCode(String secretKey, int code) {
        return gAuth.authorize(secretKey, code);
    }
}