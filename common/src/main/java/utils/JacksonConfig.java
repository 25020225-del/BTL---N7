package utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Thread-safe centralized Jackson data mapper configuration lifecycle manager.
 * Guarantees uniform ISO-8601 temporal parsing across distributed remote transport nodes.
 */
public final class JacksonConfig {

    private static final ObjectMapper INSTANCE = buildMapper();

    private JacksonConfig() {
    }

    /**
     * Yields the shared thread-safe global object serialization mapper instance.
     *
     * @return singleton {@link ObjectMapper} reference worker
     */
    public static ObjectMapper mapper() {
        return INSTANCE;
    }

    private static ObjectMapper buildMapper() {
        ObjectMapper mapper = new ObjectMapper();

        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
        mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

        return mapper;
    }
}