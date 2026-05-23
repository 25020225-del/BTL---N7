package service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Service for uploading images to Cloudinary CDN.
 *
 * <p>Credentials are read from the following environment variables at startup:</p>
 * <ul>
 *   <li>{@code CLOUDINARY_CLOUD_NAME}</li>
 *   <li>{@code CLOUDINARY_API_KEY}</li>
 *   <li>{@code CLOUDINARY_API_SECRET}</li>
 * </ul>
 *
 * <p>The application will throw {@link IllegalStateException} on startup
 * if any variable is missing, preventing silent credential misconfiguration.</p>
 */
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);
    private static final Cloudinary cloudinary;

    private static final io.github.cdimascio.dotenv.Dotenv dotenv;

    static {
        // Tự động tìm và load file .env ở thư mục gốc của dự án
        dotenv = io.github.cdimascio.dotenv.Dotenv.configure().ignoreIfMissing().load();

        String cloudName = getEnvOrDotenv("CLOUDINARY_CLOUD_NAME");
        String apiKey = getEnvOrDotenv("CLOUDINARY_API_KEY");
        String apiSecret = getEnvOrDotenv("CLOUDINARY_API_SECRET");

        cloudinary = new Cloudinary(com.cloudinary.utils.ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret
        ));
    }

    private static String getEnvOrDotenv(String key) {
        // Cách hoạt động: Tìm trong hệ thống trước, nếu không thấy thì lật file .env ra tìm
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = dotenv.get(key);
        }

        // Nếu tìm cả 2 nơi vẫn không thấy thì mới báo lỗi
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable '" + key + "' is not set. "
                            + "Configure it in System Environment or .env file before starting the server."
            );
        }
        return value;
    }

    /**
     * Uploads a byte array (compressed image) to Cloudinary and returns the secure HTTPS URL.
     *
     * @param imageBytes The raw bytes of the image to upload.
     * @return The secure HTTPS URL of the uploaded image.
     * @throws IOException if the upload fails or the CDN is unreachable.
     */
    public static String uploadImage(byte[] imageBytes) throws IOException {
        Map uploadResult = cloudinary.uploader().upload(imageBytes, ObjectUtils.emptyMap());
        String url = (String) uploadResult.get("secure_url");
        log.info("Image uploaded successfully to Cloudinary: {}", url);
        return url;
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable '" + key + "' is not set. "
                            + "Configure it before starting the server."
            );
        }
        return value;
    }
}