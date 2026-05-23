package client.utils;

import client.network.ErrorPayload;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ErrorParser {
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Extracts and flattens user-friendly error messages from server response payloads.
     * Safely handles legacy string fallback packages and structural ErrorPayload data models.
     *
     * @param data Raw untyped network packet data object.
     * @return Sanitized error string map to be displayed on UI alert prompts.
     */
    public static String parse(Object data) {
        if (data == null) {
            return "Unknown internal server error.";
        }

        // Fallback boundary: if server returns a raw string message
        if (data instanceof String) {
            return (String) data;
        }

        // Standard boundary: read serialized secure structural ErrorPayload model mapping
        try {
            ErrorPayload payload = mapper.convertValue(data, ErrorPayload.class);
            return payload.getErrorMessage();
        } catch (IllegalArgumentException e) {
            return "Malformed error package received from server.";
        }
    }
}