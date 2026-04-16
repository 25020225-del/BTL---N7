package gui;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

public class QRCodeHelper {

    public static final String ANSI_RESET  = "\u001B[0m";
    public static final String ANSI_RED    = "\u001B[31m";
    public static final String ANSI_GREEN  = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE   = "\u001B[34m";

    public static Image generateQRCodeImage(String text, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text,BarcodeFormat.QR_CODE,width,height);

            WritableImage image = new WritableImage(width,height);
            PixelWriter pw = image.getPixelWriter();

            // Draw
            for (int x=0;x<width;x++) {
                for (int y=0;y<height;y++) {
                    pw.setArgb(x,y,bitMatrix.get(x,y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            return image;

        } catch (Exception e) {
            System.out.println("[System]: QR code creation error: "+ANSI_RED+e.getMessage()+ANSI_RESET);
            return null;
        }
    }
}