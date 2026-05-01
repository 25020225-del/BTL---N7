package gui.widget;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * A custom UI Label widget that displays a live countdown timer.
 * Synchronized tightly with the server's authoritative time.
 */
public class CountdownClock extends Label {
    private Timeline timeline;
    private long endTime;

    public CountdownClock() {
        this.setText("00:00:00");
    }

    /**
     * Starts the countdown timer against a designated ending timestamp.
     *
     * @param endTimeTimestamp The absolute end time in milliseconds.
     */
    public void start(long endTimeTimestamp) {
        this.endTime = endTimeTimestamp;

        if (timeline != null) timeline.stop();

        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {

            // TIME SYNC APPLIED: Calculate the difference using the synchronized server time
            // rather than the client's potentially inaccurate local machine clock.
            long diff = endTime - utils.TimeUtil.getCurrentServerTime();

            if (diff <= 0) {
                this.setText("00:00:00");
                timeline.stop();
            } else {
                this.setText(formatTime(diff));
            }
        }));

        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    public long getEndTime() {
        return endTime;
    }

    /**
     * Formats raw milliseconds into a human-readable duration string.
     */
    private String formatTime(long millis) {
        if (millis <= 0) return "Hết thời gian";

        java.time.Duration d = java.time.Duration.ofMillis(millis);

        long seconds = d.toSeconds();
        long minutes = d.toMinutes();
        long hours = d.toHours();
        long days = d.toDays();

        // Under 1 hour -> mm:ss
        if (hours < 1) {
            return String.format("%02d:%02d", d.toMinutesPart(), d.toSecondsPart());
        }

        // Under 24 hours -> hh:mm
        if (days < 1) {
            return String.format("%02d giờ %02d phút", hours, d.toMinutesPart());
        }

        // Under 7 days -> X days
        if (days < 7) {
            return days + " ngày";
        }

        // Under 30 days -> X weeks
        if (days < 30) {
            long weeks = days / 7;
            return weeks + " tuần";
        }

        // Under 365 days -> X months
        if (days < 365) {
            long months = days / 30; // Rough 30-day average estimate
            return months + " tháng";
        }

        // Remainder -> Years
        long years = days / 365;
        return years + " năm";
    }

    public void stop() {
        if (timeline != null) timeline.stop();
    }
}