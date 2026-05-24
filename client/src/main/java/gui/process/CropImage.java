package gui.process;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aspect-ratio preserving viewport mapping utility for graphical nodes.
 * Computes deterministic scale-to-cover dimensions to guarantee layout containment centering
 * without structural distortion.
 */
public class CropImage {
    private static final Logger log = LoggerFactory.getLogger(CropImage.class);

    /**
     * Reconfigures the viewport constraints of an ImageView target to enforce a central-cover clip.
     *
     * @param imageView the target layout container receiving the image
     * @param image     the source binary graphical asset
     * @param width     the bounded bounding width of the target viewport
     * @param height    the bounded bounding height of the target viewport
     */
    public static void cropImage(ImageView imageView, Image image, int width, int height) {
        if (image == null || image.isError()) {
            log.warn("Image initialization failed or resource is corrupted.");
            return;
        }

        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        imageView.setPreserveRatio(false);

        double sourceW = image.getWidth();
        double sourceH = image.getHeight();

        double scale = Math.max((double) width / sourceW, (double) height / sourceH);

        double actualCropW = width / scale;
        double actualCropH = height / scale;

        double x = (sourceW - actualCropW) / 2.0;
        double y = (sourceH - actualCropH) / 2.0;

        imageView.setImage(image);
        imageView.setViewport(new Rectangle2D(x, y, actualCropW, actualCropH));
    }
}