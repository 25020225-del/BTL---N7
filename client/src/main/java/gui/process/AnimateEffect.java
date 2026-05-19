package gui.process;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
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

    public static void pauseNode(Node node, int timeSecond) {
        node.setDisable(true);
        PauseTransition pauseTransition = new PauseTransition(Duration.seconds(timeSecond));
        pauseTransition.setOnFinished(event -> {
            node.setDisable(false);
        });
        pauseTransition.play();
    }
}