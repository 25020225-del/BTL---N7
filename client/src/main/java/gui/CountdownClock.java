package gui;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.util.Duration;

public class CountdownClock extends Label {
    private Timeline timeline;
    private long endTime;

    public CountdownClock() {
        this.setText("00:00:00");
    }

    public void start(long endTimeTimestamp) {
        this.endTime = endTimeTimestamp;

        if (timeline != null) timeline.stop();

        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long diff = endTime - System.currentTimeMillis();

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
    private String formatTime(long millis) {
        if (millis <= 0) return "Hết thời gian";

        java.time.Duration d = java.time.Duration.ofMillis(millis);

        long seconds = d.toSeconds();
        long minutes = d.toMinutes();
        long hours = d.toHours();
        long days = d.toDays();

        // 1. Nếu dưới 1 tiếng -> hiển thị phút và giây (mm:ss)
        if (hours < 1) {
            return String.format("%02d:%02d", d.toMinutesPart(), d.toSecondsPart());
        }

        // 2. Nếu dưới 24 tiếng -> hiển thị giờ và phút (hh:mm)
        if (days < 1) {
            return String.format("%02d giờ %02d phút", hours, d.toMinutesPart());
        }

        // 3. Nếu dưới 7 ngày -> hiển thị số ngày
        if (days < 7) {
            return days + " ngày";
        }

        // 4. Nếu dưới 30 ngày -> hiển thị số tuần
        if (days < 30) {
            long weeks = days / 7;
            return weeks + " tuần";
        }

        // 5. Nếu dưới 365 ngày -> hiển thị số tháng
        if (days < 365) {
            long months = days / 30; // Ước tính trung bình 30 ngày/tháng
            return months + " tháng";
        }

        // 6. Phần còn lại -> Hiển thị năm
        long years = days / 365;
        return years + " năm";
    }

    public void stop() {
        if (timeline != null) timeline.stop();
    }
}
