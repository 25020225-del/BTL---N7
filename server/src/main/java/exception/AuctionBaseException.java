package exception;

/**
 * Class gốc cho toàn bộ các ngoại lệ nghiệp vụ của Hệ thống Đấu giá.
 * Kế thừa RuntimeException để không bắt buộc phải khai báo throws ở mọi method.
 */
public abstract class AuctionBaseException extends RuntimeException {
    private final String errorCode;

    public AuctionBaseException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}