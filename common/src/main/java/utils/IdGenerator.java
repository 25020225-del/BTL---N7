package utils;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * High-entropy identity generator utility framework.
 * Resolves IDOR exposure hazards and maximizes SQLite sequential indexing locality records.
 */
public final class IdGenerator {

    private static final SecureRandom random = new SecureRandom();
    private static final char[] BASE62_CHARS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private IdGenerator() {
    }

    /**
     * Synthesizes an atomized time-ordered sequential UUIDv7 token string.
     * Allocates exactly 48 high bits for millisecond epoch mappings to minimize layout b-tree balance splits.
     *
     * @return a clean 32-character continuous hexadecimal tracking token (hyphens evicted)
     */
    public static String generateUUIDv7() {
        long value = System.currentTimeMillis();
        long high = value << 16;

        high |= 0x7000L;
        high |= (random.nextLong() >>> 48) & 0x0FFFL;

        long low = random.nextLong();
        low &= 0x3FFFFFFFFFFFFFFFL;
        low |= 0x8000000000000000L;

        return new UUID(high, low).toString().replace("-", "");
    }

    /**
     * Formats an unpredictable compact Base62 reference tracking code string appended onto shortened timestamp offsets.
     *
     * @param prefix       custom system domain identifier header (e.g., 'DEP-', 'WD-')
     * @param randomLength precision width count of appended trailing high-entropy random blocks
     * @return completed unique compact trace string payload token
     */
    public static String generateSecureShortId(String prefix, int randomLength) {
        StringBuilder sb = new StringBuilder(prefix != null ? prefix : "");

        long timestamp = System.currentTimeMillis();
        while (timestamp > 0) {
            sb.append(BASE62_CHARS[(int) (timestamp % 62)]);
            timestamp /= 62;
        }

        for (int i = 0; i < randomLength; i++) {
            sb.append(BASE62_CHARS[random.nextInt(62)]);
        }

        return sb.toString();
    }
}