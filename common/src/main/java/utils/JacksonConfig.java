package utils;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Centralized Jackson {@link ObjectMapper} configuration shared across
 * both the client and server modules.
 *
 * <p>Problem this solves: Previously, each class instantiated its own
 * {@code new ObjectMapper()} with inconsistent settings, causing
 * {@link java.time.LocalDateTime} fields to either throw exceptions or
 * serialize as verbose arrays (e.g., [2024,5,1,12,0,0]) instead of
 * ISO-8601 strings (e.g., "2024-05-01T12:00:00").
 *
 * <p>All modules are registered once and the instance is reused everywhere.
 *
 * <h3>Usage — replace every {@code new ObjectMapper()} with:</h3>
 * <pre>
 *   // Before:
 *   private final ObjectMapper mapper = new ObjectMapper()
 *       .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
 *
 *   // After:
 *   private final ObjectMapper mapper = JacksonConfig.mapper();
 * </pre>
 *
 * <h3>Dependency required in pom.xml (common module):</h3>
 * <pre>{@code
 * <dependency>
 *     <groupId>com.fasterxml.jackson.datatype</groupId>
 *     <artifactId>jackson-datatype-jsr310</artifactId>
 *     <version>2.17.0</version>
 * </dependency>
 * }</pre>
 */
public final class JacksonConfig {

    /**
     * Thread-safe singleton instance.
     * Initialized once via a static inner holder (Bill Pugh pattern).
     */
    private static final ObjectMapper INSTANCE = buildMapper();

    // Prevent instantiation
    private JacksonConfig() {}

    /**
     * Returns the shared, fully-configured {@link ObjectMapper} instance.
     *
     * @return The singleton {@link ObjectMapper}.
     */
    public static ObjectMapper mapper() {
        return INSTANCE;
    }

    // =========================================================================
    // INTERNAL BUILDER
    // =========================================================================

    private static ObjectMapper buildMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // --- Java Time Support (LocalDateTime, LocalDate, Instant, etc.) ---
        // Registers serializers/deserializers for all java.time.* types.
        mapper.registerModule(new JavaTimeModule());

        // Write LocalDateTime as ISO-8601 string: "2024-05-01T12:00:00"
        // Without this, it serializes as a numeric timestamp array: [2024,5,1,12,0,0]
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // --- Robustness settings (was already set in most handlers) ---
        // Ignore unknown JSON fields when deserializing into a model object.
        // Prevents crashes when the server adds a new field the old client doesn't know about.
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Ignore unknown enum values — treat them as null instead of throwing.
        // Useful if an enum gets a new value added on the server side.
        mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);

        // Remove null fields from JSON strings during compression (Serialize).
        // Useful in reducing bandwidth when transmitting data.
        mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

        return mapper;
    }
}