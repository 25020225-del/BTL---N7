package gui.process;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * System utility housing chronological architecture transformations. Ensures matching parameters
 * between internal long integer epoch timestamps and object temporal systems.
 */
public class Convert {

    /**
     * Evaluates a system Unix epoch millisecond matrix and transforms it into localized date-time structures.
     */
    public static LocalDateTime longToTimestamp(long millis) {
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(millis),
                ZoneId.systemDefault()
        );
    }

    /**
     * Deconstructs standard localized temporal fields into scalar long integer epoch representations.
     */
    public static long timestampToLong(LocalDateTime ldt) {
        return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}