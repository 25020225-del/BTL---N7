package gui.process;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CropImage {
    private static Logger log = LoggerFactory.getLogger(CropImage.class);

    public static void cropImage(ImageView imageView, Image image, int width, int height) {
        if (image == null || image.isError()) {
            log.warn("Image is null or error");
            return;
        }

        // 1. Cấu hình kích thước khung nhìn cho ImageView
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        imageView.setPreserveRatio(false); // Tắt để vùng cắt (Viewport) lấp đầy khung

        // 2. Lấy kích thước thực tế của ảnh gốc
        double sourceW = image.getWidth();
        double sourceH = image.getHeight();

        // 3. Tính toán tỷ lệ để bao phủ (Cover) khung hình
        // Chúng ta ép kiểu int sang double ở đây để tính toán chính xác
        double scale = Math.max((double) width / sourceW, (double) height / sourceH);

        // 4. Tính toán kích thước vùng cắt trên ảnh gốc
        double actualCropW = width / scale;
        double actualCropH = height / scale;

        // 5. Xác định tọa độ X, Y để vùng cắt nằm chính giữa ảnh
        double x = (sourceW - actualCropW) / 2.0;
        double y = (sourceH - actualCropH) / 2.0;

        // 6. Áp dụng ảnh và vùng cắt vào ImageView
        imageView.setImage(image);
        imageView.setViewport(new Rectangle2D(x, y, actualCropW, actualCropH));
    }
}
