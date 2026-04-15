package service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;

public class TOTPService {
    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    // 1. Tạo chìa khóa bí mật mới
    public String createSecretKey() {
        final GoogleAuthenticatorKey key = gAuth.createCredentials();
        return key.getKey();
    }

    // 2. Tạo đường link để Client vẽ mã QR (Dùng tên App là "AuctionSystem-N7")
    public String getQRUrl(String username, String secretKey) {
        return GoogleAuthenticatorQRGenerator.getOtpAuthURL("AuctionSystem-N7", username,
                new GoogleAuthenticatorKey.Builder(secretKey).build());
    }

    // 3. Kiểm tra 6 con số người dùng nhập vào có khớp không
    public boolean verifyCode(String secretKey, int code) {
        return gAuth.authorize(secretKey, code);
    }
}