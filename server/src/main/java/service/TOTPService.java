package service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import java.net.URLEncoder;

public class TOTPService {
    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    // 1. Tạo chìa khóa bí mật mới
    public String createSecretKey() {
        final GoogleAuthenticatorKey key = gAuth.createCredentials();
        return key.getKey();
    }

    // 2. Tạo CHUỖI BẢO MẬT CHUẨN để Client vẽ mã QR
    public String getQRUrl(String username, String secretKey) {
        try {
            String issuer = "AuctionSystem-N7";
            // Mã hóa username phòng trường hợp tài khoản có dấu cách hoặc ký tự đặc biệt
            String encodedUsername = URLEncoder.encode(username, "UTF-8").replace("+", "%20");

            // Đây mới là định dạng chuẩn mà Google Authenticator đọc được
            return "otpauth://totp/" + issuer + ":" + encodedUsername + "?secret=" + secretKey + "&issuer=" + issuer;
        } catch (Exception e) {
            e.printStackTrace();
            return "otpauth://totp/AuctionSystem-N7:" + username + "?secret=" + secretKey + "&issuer=AuctionSystem-N7";
        }
    }

    // 3. Kiểm tra 6 con số người dùng nhập vào có khớp không
    public boolean verifyCode(String secretKey, int code) {
        return gAuth.authorize(secretKey, code);
    }
}