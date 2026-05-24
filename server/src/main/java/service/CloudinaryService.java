package service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Service orchestrating binary image assets uploading pipelines into Cloudinary CDN.
 * Enforces fail-fast validation checks over foundational environment credentials on startup.
 */
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);
    private static final Cloudinary cloudinary;
    private static final io.github.cdimascio.dotenv.Dotenv dotenv;

    static {
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
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = dotenv.get(key);
        }

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable '" + key + "' is not set. "
                            + "Configure it in System Environment or .env file before starting the server."
            );
        }
        return value;
    }

    /**
     * Uploads a raw compressed byte array payload to the target Cloudinary CDN repository.
     *
     * @param imageBytes the compressed binary array representation of the target image
     * @return secure HTTPS endpoint resource locator string mapped by the CDN gateway
     * @throws IOException if network integration drops or endpoint is unreachable
     */
    public static String uploadImage(byte[] imageBytes) throws IOException {
        Map uploadResult = cloudinary.uploader().upload(imageBytes, ObjectUtils.emptyMap());
        String url = (String) uploadResult.get("secure_url");
        log.info("Image uploaded successfully to Cloudinary: {}", url);
        return url;
    }
}