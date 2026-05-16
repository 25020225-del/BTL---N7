package client.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import client.network.ErrorPayload; // [FIXED]: Sửa đường dẫn import tránh lỗi Split Package

public class ErrorParser {
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Bóc tách thông điệp lỗi từ payload của máy chủ.
     */
    public static String parse(Object data) {
        if (data == null) {
            return "Lỗi không xác định từ máy chủ.";
        }

        // 1. Nếu Server vẫn còn luồng nào đó lỡ gửi String trần (fallback an toàn)
        if (data instanceof String) {
            return (String) data;
        }

        // 2. Nếu Server gửi object ErrorPayload chuẩn
        try {
            ErrorPayload payload = mapper.convertValue(data, ErrorPayload.class);
            return payload.getErrorMessage();
        } catch (IllegalArgumentException e) {
            return "Không thể đọc dữ liệu lỗi từ máy chủ. (Lỗi định dạng)";
        }
    }
}