package gui.process;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import static utils.ConsoleColors.*;

/**
 * A utility class for generating Quick Response (QR) codes.
 * This class utilizes the Google ZXing library to encode text data into a 2D barcode
 * and converts it directly into a JavaFX {@link Image} for UI rendering.
 */
public final class QRCodeHelper {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private QRCodeHelper() {}

    /**
     * Generates a JavaFX Image containing a QR code representing the provided text.
     *
     * @param text   The content or data (e.g., URL, secret key) to encode into the QR code.
     * @param width  The desired width of the generated QR code image in pixels.
     * @param height The desired height of the generated QR code image in pixels.
     * @return A JavaFX {@link Image} object displaying the QR code, or {@code null} if an error occurs during encoding.
     */
    public static Image generateQRCodeImage(String text, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);

            WritableImage image = new WritableImage(width, height);
            PixelWriter pixelWriter = image.getPixelWriter();

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int color = bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
                    pixelWriter.setArgb(x, y, color);
                }
            }

            return image;

        } catch (Exception e) {
            System.out.println("[Error]: QR code creation failed: " + RED + e.getMessage() + RESET);
            return null;
        }
    }
}