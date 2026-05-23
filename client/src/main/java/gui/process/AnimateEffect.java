package gui.process;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Utility class to handle smooth UI animations and visual effects.
 */
public class AnimateEffect {

    /**
     * Applies a quick flashing fade transition to draw user attention to a specific UI node
     * (e.g., when a new price update arrives via WebSocket).
     *
     * @param node The JavaFX Node to be highlighted.
     */
    public static void highlightText(Node node) {
        FadeTransition ft = new FadeTransition(Duration.millis(150), node);
        ft.setFromValue(1.0);
        ft.setToValue(0.3);
        ft.setCycleCount(4); // Flash twice (down, up, down, up)
        ft.setAutoReverse(true);
        ft.play();
    }

    public static void applyFadeAndTranslate(Node node) {
        Duration duration = Duration.millis(600);

        // 1. Cấu hình hiệu ứng Mờ dần (Fade)
        FadeTransition fadeIn = new FadeTransition(duration, node);
        fadeIn.setFromValue(0.0); // Bắt đầu từ trong suốt
        fadeIn.setToValue(1.0);   // Hiện rõ hoàn toàn

        // 2. Cấu hình hiệu ứng Dịch chuyển (Translate)
        TranslateTransition moveUp = new TranslateTransition(duration, node);
        moveUp.setFromY(50);      // Xuất phát ở vị trí thấp hơn vị trí gốc 50 pixel
        moveUp.setToY(0);         // Bay về đúng vị trí chuẩn theo thiết kế FXML

        // 🛠️ Mẹo nhỏ: Thêm bộ gia tốc EASE_OUT để lúc gần đích nó "phanh" chậm lại nhìn rất mượt
        fadeIn.setInterpolator(Interpolator.EASE_OUT);
        moveUp.setInterpolator(Interpolator.EASE_OUT);

        // 3. TRUNG TÂM XỬ LÝ: Gom cả 2 vào ParallelTransition để ép chạy SONG SONG
        ParallelTransition parallel = new ParallelTransition(fadeIn, moveUp);

        // Hoặc bạn có thể dùng cách này nếu muốn add sau:
        // parallel.getChildren().addAll(fadeIn, moveUp);

        // 4. Kích hoạt chạy
        parallel.play();
    }

    public static void pauseNode(Node node, int timeSecond) {
        node.setDisable(true);
        PauseTransition pauseTransition = new PauseTransition(Duration.seconds(timeSecond));
        pauseTransition.setOnFinished(event -> {
            node.setDisable(false);
        });
        pauseTransition.play();
    }
}