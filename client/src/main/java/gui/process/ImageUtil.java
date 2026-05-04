package gui.process;

import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;

import static utils.ConsoleColors.*;

/**
 * Utility class for handling image conversions.
 * Converts local image files to Base64 strings for network transmission,
 * and decodes Base64 strings back to JavaFX Image objects.
 */
public class ImageUtil {

    /**
     * Reads a local file and encodes it into a Base64 string.
     *
     * @param file The local image file selected by the user.
     * @return The Base64 encoded string representing the image, or null if an error occurs.
     */
    public static String encodeImageToBase64(File file) {
        if (file == null || !file.exists()) {
            System.out.println("[Warning]: Image file not found or is null.");
            return null;
        }

        try {
            byte[] fileContent = Files.readAllBytes(file.toPath());

            // Check file size (e.g., limit to ~2MB) to prevent overwhelming the server/WebSocket
            if (fileContent.length > 2 * 1024 * 1024) {
                System.out.println("[Warning]: " + YELLOW + "Image size exceeds 2MB. Transmission might be slow." + RESET);
            }

            return Base64.getEncoder().encodeToString(fileContent);
        } catch (Exception e) {
            System.out.println("[Error]: Failed to encode image to Base64: " + RED + e.getMessage() + RESET);
            return null;
        }
    }

    /**
     * Decodes a Base64 string back into a JavaFX Image for UI rendering.
     *
     * @param base64String The Base64 encoded image string received from the server.
     * @return A JavaFX Image object, or null if the string is invalid or empty.
     */
    public static Image decodeBase64ToImage(String base64String) {
        if (base64String == null || base64String.trim().isEmpty()) {
            return null;
        }

        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64String);
            return new Image(new ByteArrayInputStream(imageBytes));
        } catch (IllegalArgumentException e) {
            System.out.println("[Error]: Invalid Base64 format received: " + RED + e.getMessage() + RESET);
            return null;
        } catch (Exception e) {
            System.out.println("[Error]: Failed to decode Base64 image: " + RED + e.getMessage() + RESET);
            return null;
        }
    }
}