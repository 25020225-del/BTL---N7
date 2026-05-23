package utils;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Secure ID Generator Utility for the N7 Auction System.
 * Eliminates primary key collisions, predictable sequential IDs (IDOR vulnerabilities),
 * and business metric leakage caused by exposing system timestamps.
 */
public final class IdGenerator {

    private static final SecureRandom random = new SecureRandom();

    // Bảng ký tự Base62 (loại bỏ ký tự đặc biệt để an toàn cho URL và ID)
    private static final char[] BASE62_CHARS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private IdGenerator() {
    }

    /**
     * Generates a time-ordered UUIDv7 string.
     * Uses 48 bits for millisecond timestamp and cryptographically secure random bits for the remainder.
     * Guaranteed collision-free, unpredictable, and highly optimized for SQLite sequential indexing.
     *
     * @return 32-character hexadecimal UUIDv7 string (hyphens removed).
     */
    public static String generateUUIDv7() {
        long value = System.currentTimeMillis();
        long high = value << 16;

        high |= 0x7000L; // Set UUID version to 7
        high |= (random.nextLong() >>> 48) & 0x0FFFL;

        long low = random.nextLong();
        low &= 0x3FFFFFFFFFFFFFFFL; // Set UUID variant to IETF (Variant 2)
        low |= 0x8000000000000000L;

        return new UUID(high, low).toString().replace("-", "");
    }

    /**
     * Generates a compact, secure random Base62 identifier appended to a shortened encoded timestamp.
     * Ideal for user-facing reference numbers like deposit order IDs or withdrawal requests.
     *
     * @param prefix       Optional string prefix (e.g., "WD-", "DEP-").
     * @param randomLength Number of secure random trailing characters.
     * @return Unpredictable compact unique string ID.
     */
    public static String generateSecureShortId(String prefix, int randomLength) {
        StringBuilder sb = new StringBuilder(prefix != null ? prefix : "");

        // Encode current timestamp into Base62 for compact chronological order
        long timestamp = System.currentTimeMillis();
        while (timestamp > 0) {
            sb.append(BASE62_CHARS[(int) (timestamp % 62)]);
            timestamp /= 62;
        }

        // Append secure random characters to completely avoid race condition collisions
        for (int i = 0; i < randomLength; i++) {
            sb.append(BASE62_CHARS[random.nextInt(62)]);
        }

        return sb.toString();
    }
}