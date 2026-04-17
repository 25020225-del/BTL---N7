package gui;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

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

                for (Node n : vBoxItem.getChildren()) {
                    if (n instanceof Label) {
                        Label label = (Label) n;

                        if (label.getText().toLowerCase().contains(searchKeyword)) {
                            found = true;
                            break;
                        }
                    }
                }

                vBoxItem.setManaged(found);
                vBoxItem.setVisible(found);
            }
        }
    }
}