package gui.process;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class Convert {
    public static LocalDateTime longToTimestamp(long millis) {
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(millis),
                ZoneId.systemDefault()
        );
    }

    /**
     * Chuyển ngược từ LocalDateTime sang Long (mili giây)
     * Thường dùng khi bạn muốn gửi thời gian từ Client lên Server
     */
    public static long timestampToLong(LocalDateTime ldt) {
        return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
