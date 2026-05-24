package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NTP-inspired network clock synchronization manager.
 * Calibrates absolute hardware system drift values to provide authoritative server-time records.
 */
public class TimeUtil {

    private static final Logger log = LoggerFactory.getLogger(TimeUtil.class);
    private static long timeOffset = 0;

    /**
     * Adjusts the internal structural runtime timestamp deviation metrics via dynamic RTT profiling calculations.
     *
     * @param clientSendTime    local epoch millisecond snapshot capturing frame launch
     * @param serverTime        authoritative clock metric stamped by inbound processing gateways
     * @param clientReceiveTime local epoch millisecond snapshot capturing return ingress frames
     */
    public static void calibrateOffset(long clientSendTime, long serverTime, long clientReceiveTime) {
        long roundTripTime = clientReceiveTime - clientSendTime;

        // Establishes a rigid technical assumption demanding network latency to be strictly symmetric
        long estimatedRealServerTime = serverTime + (roundTripTime / 2);
        timeOffset = estimatedRealServerTime - clientReceiveTime;

        log.debug("Network RTT: {}ms | Offset adjusted by {}ms", roundTripTime, timeOffset);
    }

    /**
     * Resolves the authoritative global unified clock epoch millisecond representation value.
     * Must be invoked as a drop-in safe alternate tracking replacement instead of raw platform system queries.
     *
     * @return current synchronized real-world epoch length values in milliseconds
     */
    public static long getCurrentServerTime() {
        return System.currentTimeMillis() + timeOffset;
    }
}