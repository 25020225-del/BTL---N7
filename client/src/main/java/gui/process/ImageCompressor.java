package gui.process;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

public class ImageCompressor {

    /**
     * Nén ảnh từ File và trả về mảng byte[].
     *
     * @param file    File ảnh gốc (png, jpg, bmp...)
     * @param quality Độ chất lượng (0.0f đến 1.0f). Gợi ý: 0.6f - 0.8f
     * @return mảng byte[] đã nén định dạng JPG
     * @throws IOException Nếu lỗi đọc ghi file
     */
    public static byte[] compressToBytes(File file, float quality) throws IOException {
        // 1. Đọc ảnh gốc
        BufferedImage originalImage = ImageIO.read(file);
        if (originalImage == null) {
            throw new IOException("Cannot read file format: " + file.getAbsolutePath());
        }

        // 2. Xử lý trường hợp ảnh có nền trong suốt (Alpha channel)
        // JPG không hỗ trợ trong suốt, nên ta tạo một ảnh mới nền TRẮNG
        BufferedImage newImage = new BufferedImage(
                originalImage.getWidth(),
                originalImage.getHeight(),
                BufferedImage.TYPE_INT_RGB // Ép về hệ màu RGB (không alpha)
        );

        Graphics2D g2d = newImage.createGraphics();
        g2d.setColor(Color.WHITE); // Đặt nền trắng
        g2d.fillRect(0, 0, newImage.getWidth(), newImage.getHeight());
        g2d.drawImage(originalImage, 0, 0, null);
        g2d.dispose();

        // 3. Chuẩn bị nén
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");

        if (!writers.hasNext()) throw new IllegalStateException(".jpg format is not available");
        ImageWriter writer = writers.next();

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);

            // Cấu hình chất lượng nén
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }

            // Ghi dữ liệu
            writer.write(null, new IIOImage(newImage, null, null), param);
        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }
}