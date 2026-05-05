package gui.process;

import net.coobird.thumbnailator.Thumbnails;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class ImageCompressor {

    /**
     * Nén ảnh bằng Thumbnailator để đạt dung lượng tối ưu (< 10KB - 20KB).
     *
     * @param file    File ảnh gốc.
     * @param quality Độ chất lượng (0.0f đến 1.0f). Để xuống < 10KB nên dùng 0.3f - 0.5f.
     * @return mảng byte[] định dạng JPG.
     * @throws IOException Nếu lỗi đọc ghi file.
     */
    public static byte[] compressToBytes(File file, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Thumbnailator tự động xử lý nền trắng cho ảnh PNG/Transparent khi xuất sang JPG
        Thumbnails.of(file)
                .size(900, 900)          // Giảm độ phân giải (Quan trọng nhất để nén xuống < 10KB)
                .outputQuality(quality)  // Thiết lập chất lượng nén (ví dụ 0.4f)
                .outputFormat("jpg")     // Ép định dạng đầu ra là JPG để tối ưu dung lượng
                .toOutputStream(baos);

        return baos.toByteArray();
    }
}