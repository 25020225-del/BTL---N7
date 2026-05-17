package exception;

/**
 * File tổng hợp toàn bộ các ngoại lệ nghiệp vụ của hệ thống đấu giá.
 */
public class AuctionExceptions {

    // 1. Class gốc (Base Exception)
    public static abstract class AuctionBaseException extends RuntimeException {
        private final String errorCode;

        public AuctionBaseException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

    // 2. Các Sub-class cụ thể (Gom hết vào đây)

    public static class InvalidPayloadException extends AuctionBaseException {
        public InvalidPayloadException(String message) {
            super("ERR_PAYLOAD_001", message);
        }
    }

    public static class AuctionClosedException extends AuctionBaseException {
        public AuctionClosedException(String message) {
            super("ERR_AUC_002", message);
        }
    }

    public static class UnauthorizedAccessException extends AuctionBaseException {
        public UnauthorizedAccessException(String message) {
            super("ERR_AUTH_003", message);
        }
    }

    public static class InsufficientFundsException extends AuctionBaseException {
        public InsufficientFundsException(String message) {
            super("ERR_WALLET_004", message);
        }
    }
}