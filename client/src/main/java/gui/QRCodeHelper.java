package gui;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

public class QRCodeHelper {

    // Hàm public static để gọi được từ bất kỳ Controller nào
    public static Image generateQRCodeImage(String text, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);

            WritableImage image = new WritableImage(width, height);
            PixelWriter pw = image.getPixelWriter();

            // Vẽ các điểm ảnh đen trắng
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    pw.setArgb(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            return image;

        } catch (Exception e) {
            System.err.println("Lỗi khi tạo mã QR: " + e.getMessage());
            return null;
        }
    }
}