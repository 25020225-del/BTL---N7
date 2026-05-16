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

    public static void slideNode(Region node, boolean show) {

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(node.widthProperty());
        node.setClip(clip);

        double targetHeight = 59.0;

        Timeline timeline = new Timeline();

        if (show) {
            node.setVisible(true);
            node.setManaged(true);

            KeyValue kvHeight = new KeyValue(node.prefHeightProperty(), targetHeight, Interpolator.EASE_BOTH);
            KeyValue kvClip = new KeyValue(clip.heightProperty(), targetHeight, Interpolator.EASE_BOTH);
            KeyFrame kf = new KeyFrame(Duration.millis(300), kvHeight, kvClip);
            timeline.getKeyFrames().add(kf);
        } else {

            KeyValue kvHeight = new KeyValue(node.prefHeightProperty(), 0, Interpolator.EASE_BOTH);
            KeyValue kvClip = new KeyValue(clip.heightProperty(), 0, Interpolator.EASE_BOTH);
            KeyFrame kf = new KeyFrame(Duration.millis(300), kvHeight, kvClip);

            timeline.getKeyFrames().add(kf);
            timeline.setOnFinished(e -> {
                node.setVisible(false);
                node.setManaged(false);
            });
        }

        timeline.play();
    }

    public static void fadeNode(Node node, boolean show) {
        FadeTransition ft = new FadeTransition(Duration.millis(300), node);
        if (show) {
            node.setManaged(true);
            ft.setFromValue(0.0);
            ft.setToValue(1.0);
            node.setVisible(true);
        } else {
            ft.setFromValue(1.0);
            ft.setToValue(0.0);

            ft.setOnFinished(e -> {
                node.setVisible(false);
                node.setManaged(false);
            });
        }
        ft.play();
    }

    public static void showOrHideItem(TilePane node, String item) {

        String searchKeyword = item.toLowerCase();

        for (Node s : node.getChildren()) {
            if (s instanceof VBox) {
                VBox vBoxItem = (VBox) s;
                boolean found = false;

                found = Search.searchText(item, s);

                vBoxItem.setManaged(found);
                vBoxItem.setVisible(found);
            }
        }
    }

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