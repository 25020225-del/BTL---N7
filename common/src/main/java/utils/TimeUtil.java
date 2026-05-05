package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to manage time synchronization between the Client and the Server.
 * Implements an NTP-like offset calculation to ensure countdowns and auction events
 * use the Server's authoritative time, regardless of the Client's local machine clock.
 */
public class TimeUtil {
    private static final Logger log = LoggerFactory.getLogger(TimeUtil.class);

    // The calculated difference in milliseconds between the Server and the Client.
    private static long timeOffset = 0;

    /**
     * Calibrates the time offset based on a Round-Trip Time (RTT) calculation.
     *
     * @param clientSendTime    The exact local time the client sent the sync request.
     * @param serverTime        The exact authoritative time the server processed the request.
     * @param clientReceiveTime The exact local time the client received the server's response.
     */
    public static void calibrateOffset(long clientSendTime, long serverTime, long clientReceiveTime) {
        // Calculate the total time taken for the packet to travel to the server and back
        long roundTripTime = clientReceiveTime - clientSendTime;

        // Estimate the exact server time at the moment the client received the packet
        // Assuming network latency is symmetric (takes the same time going and coming back)
        long estimatedRealServerTime = serverTime + (roundTripTime / 2);

        // Save the offset: how far ahead or behind the server is compared to local time
        timeOffset = estimatedRealServerTime - clientReceiveTime;

        log.debug("Network RTT: {}ms | Offset adjusted by {}ms", roundTripTime, timeOffset);
    }

    /**
     * Returns the synchronized authoritative server time.
     * Use this method instead of System.currentTimeMillis() for all time-critical logic.
     *
     * @return The synchronized current time in milliseconds.
     */
    public static long getCurrentServerTime() {
        return System.currentTimeMillis() + timeOffset;
    }
}