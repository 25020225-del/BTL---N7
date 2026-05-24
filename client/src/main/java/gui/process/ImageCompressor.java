package gui.process;

import net.coobird.thumbnailator.Thumbnails;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * Binary asset transformation engine. Enforces data layout restrictions via
 * downsampling and spatial pipeline compression mechanics to optimize network egress.
 */
public class ImageCompressor {

    /**
     * Compresses an absolute file handle into an operational byte matrix formatted to standard JPEG.
     *
     * @param file    the raw target binary graphical asset file
     * @param quality the targeted structural compression scale variable ranging from 0.0f to 1.0f
     * @return a compressed byte array block ready for transport packing
     * @throws IOException if local file storage access routines throw errors
     */
    public static byte[] compressToBytes(File file, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Thumbnails.of(file)
                .size(900, 900)
                .outputQuality(quality)
                .outputFormat("jpg")
                .toOutputStream(baos);

        return baos.toByteArray();
    }
}