package exception;

/**
 * Enterprise domain exception mapping matrix for the auction system.
 * Provides distinct error schemas and structured error tracing capabilities.
 */
public class AuctionExceptions {

    /**
     * Abstract root component for all specialized business processing errors.
     */
    public static abstract class AuctionBaseException extends RuntimeException {
        private final String errorCode;

        /**
         * System business exception initializer.
         *
         * @param errorCode structural unique error identifier code
         * @param message   descriptive technical failure statement
         */
        public AuctionBaseException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

    /**
     * Thrown when an incoming network payload breaks system serialization or format constraints.
     */
    public static class InvalidPayloadException extends AuctionBaseException {
        public InvalidPayloadException(String message) {
            super("ERR_PAYLOAD_001", message);
        }
    }

    /**
     * Thrown when an interaction context attempts to alter state on an unmodifiable closed auction.
     */
    public static class AuctionClosedException extends AuctionBaseException {
        public AuctionClosedException(String message) {
            super("ERR_AUC_002", message);
        }
    }

    /**
     * Thrown when access validation chains intercept a resource interaction request.
     */
    public static class UnauthorizedAccessException extends AuctionBaseException {
        public UnauthorizedAccessException(String message) {
            super("ERR_AUTH_003", message);
        }
    }

    /**
     * Thrown when a financial ledger checkpoint fails to pull sufficient available asset reserves.
     */
    public static class InsufficientFundsException extends AuctionBaseException {
        public InsufficientFundsException(String message) {
            super("ERR_WALLET_004", message);
        }
    }
}