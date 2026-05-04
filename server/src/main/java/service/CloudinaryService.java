package service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import java.util.Map;

public class CloudinaryService {
    private static final Cloudinary cloudinary;

    static {
        // Khởi tạo cấu hình (Thay bằng thông số thực tế của bạn)
        cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", "de1isjzur",
                "api_key", "748352485873877",
                "api_secret", "_6OeXCYYcSL05fVZmqyGh3vtgvg"
        ));
    }

    /**
     * Upload mảng byte ảnh lên Cloudinary
     *
     * @param imageBytes Mảng byte của ảnh (đã nén)
     * @return URL của ảnh sau khi upload thành công
     */
    public static String uploadImage(byte[] imageBytes) {
        try {
            Map uploadResult = cloudinary.uploader().upload(imageBytes, ObjectUtils.emptyMap());
            return (String) uploadResult.get("secure_url"); // Trả về URL dạng https
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}