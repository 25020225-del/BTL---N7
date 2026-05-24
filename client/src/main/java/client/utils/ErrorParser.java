package client.utils;

import client.network.ErrorPayload;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility parser designed to unwrap and normalize error payloads received from the server network layer.
 */
public final class ErrorParser {
    private static final ObjectMapper mapper = new ObjectMapper();

    private ErrorParser() {
    }

    /**
     * Extracts an intelligible error message string from raw polymorphic server response payloads.
     *
     * @param data the raw untyped network packet data object
     * @return a user-friendly sanitized error string message
     */
    public static String parse(Object data) {
        if (data == null) {
            return "Unknown internal server error.";
        }

        if (data instanceof String) {
            return (String) data;
        }

        try {
            ErrorPayload payload = mapper.convertValue(data, ErrorPayload.class);
            return payload.getErrorMessage();
        } catch (IllegalArgumentException e) {
            return "Malformed error package received from server.";
        }
    }
}