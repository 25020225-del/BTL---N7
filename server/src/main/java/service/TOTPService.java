package service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import java.net.URLEncoder;

import static utils.ConsoleColors.*;

public class TOTPService {

    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    public String createSecretKey() {
        return gAuth.createCredentials().getKey();
    }

    public String getQRUrl(String username, String secretKey) {
        try {
            String issuer = "AuctionSystem-N7";
            String encodedUsername = URLEncoder.encode(username, "UTF-8").replace("+", "%20");
            return "otpauth://totp/" + issuer + ":" + encodedUsername + "?secret=" + secretKey + "&issuer=" + issuer;
        } catch (Exception e) {
            System.out.println("[Error]: URL Encoding failed: " + RED + e.getMessage() + RESET);
            e.printStackTrace();
            return "otpauth://totp/AuctionSystem-N7:" + username + "?secret=" + secretKey + "&issuer=AuctionSystem-N7";
        }
    }

    public boolean verifyCode(String secretKey, int code) {
        return gAuth.authorize(secretKey, code);
    }
}