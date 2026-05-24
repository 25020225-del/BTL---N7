package gui.widget;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * Reactive JavaFX Label implementation providing active real-time time tracking.
 * Synchronizes hardware system time slices continuously using synchronized server authoritative offsets.
 */
public class CountdownClock extends Label {
    private Timeline timeline;
    private long endTime;

    public CountdownClock() {
        this.setText("00:00:00");
    }

    /**
     * Binds a target termination epoch timestamp and fires the continuous 1-second interval ticker.
     *
     * @param endTimeTimestamp target authoritative absolute server expiration timestamp in milliseconds
     */
    public void start(long endTimeTimestamp) {
        this.endTime = endTimeTimestamp;
        if (timeline != null) timeline.stop();

        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long diff = endTime - utils.TimeUtil.getCurrentServerTime();
            this.setText(formatTime(diff));
            if (diff <= 0) {
                timeline.stop();
            }
        }));

        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    public void stop() {
        if (timeline != null) {
            timeline.stop();
        }
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    private String formatTime(long millis) {
        if (millis <= 0) return "Time's up";

        java.time.Duration d = java.time.Duration.ofMillis(millis);
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
}