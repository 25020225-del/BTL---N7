package gui.widget;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * A custom JavaFX {@link Label} that acts as a real-time countdown timer.
 * It synchronizes with the authoritative server time to continuously display
 * the remaining duration until a specified expiration timestamp.
 */
public class CountdownClock extends Label {
    private Timeline timeline;
    private long endTime;

    /**
     * Initializes the countdown clock with a default display of "00:00:00".
     */
    public CountdownClock() {
        this.setText("00:00:00");
    }

    /**
     * Starts the countdown sequence targeting the given expiration timestamp.
     * The clock updates its text every second based on the synchronized server time.
     *
     * @param endTimeTimestamp The target expiration time in milliseconds.
     */
    public void start(long endTimeTimestamp) {
        this.endTime = endTimeTimestamp;
        if (timeline != null) timeline.stop();

        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
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

    /**
     * Retrieves the target expiration timestamp of this clock.
     *
     * @return The expiration time in milliseconds.
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * Formats the remaining time in milliseconds into a human-readable string representation.
     * The format dynamically changes based on the magnitude of the remaining time
     * (e.g., minutes/seconds, hours/minutes, days, weeks).
     *
     * @param millis The remaining time in milliseconds.
     * @return A formatted string representing the remaining duration.
     */
    private String formatTime(long millis) {
        if (millis <= 0) return "Time's up";

        java.time.Duration d = java.time.Duration.ofMillis(millis);

        long seconds = d.toSeconds();
        long minutes = d.toMinutes();
        long hours = d.toHours();
        long days = d.toDays();

        if (hours < 1) {
            return String.format("%02d:%02d", d.toMinutesPart(), d.toSecondsPart());
        }
        if (days < 1) {
            return String.format("%02d hrs %02d mins", hours, d.toMinutesPart());
        }
        if (days < 7) {
            return days + " days";
        }
        if (days < 30) {
            return (days / 7) + " weeks";
        }
        if (days < 365) {
            return (days / 30) + " months";
        }
        return (days / 365) + " years";
    }

    /**
     * Stops the active countdown timeline, halting any further UI updates.
     */
    public void stop() {
        if (timeline != null) timeline.stop();
    }
}