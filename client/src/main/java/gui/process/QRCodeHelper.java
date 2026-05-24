package gui.process;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Encapsulates transactional utility routines targeting matrix-to-bitmap transformations.
 * Bridges external structural QR encoding architectures directly with the presentation layer asset models.
 */
public final class QRCodeHelper {
    private static final Logger log = LoggerFactory.getLogger(QRCodeHelper.class);

    private QRCodeHelper() {
    }

    /**
     * Synthesizes an uncompressed visual asset containing a two-dimensional matrix-encoded data payload.
     *
     * @param text   the core canonical data payload string to translate into matrix coordinates
     * @param width  the bounded dimension defining the target layout width allocation
     * @param height the bounded dimension defining the target layout height allocation
     * @return a stateful image context displaying the formatted matrix data, or {@code null} if generation fails
     */
    public static Image generateQRCodeImage(String text, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();

            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H,
                    EncodeHintType.MARGIN, 4
            );

            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height, hints);

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
            log.error("Structural mapping to 2D matrix layout failed: {}", e.getMessage());
            return null;
        }
    }
}