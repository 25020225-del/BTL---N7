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

    private static void slideNode(Region node, boolean show) {
        // 1. Tạo một cái "kéo" (Clip) để cắt phần nội dung tràn ra ngoài khi thu nhỏ
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(node.widthProperty());
        node.setClip(clip);

        // 2. Xác định chiều cao mục tiêu (trong FXML của bạn thanh find cao 59.0)
        double targetHeight = 59.0;

        Timeline timeline = new Timeline();

        if (show) {
            node.setVisible(true);
            node.setManaged(true);
            // Chạy từ 0 đến 59
            KeyValue kvHeight = new KeyValue(node.prefHeightProperty(), targetHeight, Interpolator.EASE_BOTH);
            KeyValue kvClip = new KeyValue(clip.heightProperty(), targetHeight, Interpolator.EASE_BOTH);
            KeyFrame kf = new KeyFrame(Duration.millis(300), kvHeight, kvClip);
            timeline.getKeyFrames().add(kf);
        } else {
            // Chạy từ 59 về 0
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


    static void fadeNode(Node node, boolean show) {
        FadeTransition ft = new FadeTransition(Duration.millis(300), node);
        if (show) {
            node.setManaged(true); // Bật managed trước để có chỗ trống
            ft.setFromValue(0.0);
            ft.setToValue(1.0);
            node.setVisible(true);
        } else {
            ft.setFromValue(1.0);
            ft.setToValue(0.0);
            // Sau khi mờ hẳn mới tắt managed và visible
            ft.setOnFinished(e -> {
                node.setVisible(false);
                node.setManaged(false);
            });
        }
        ft.play();
    }
    public static void showOrHideItem(TilePane node, String item) {
        for(Node s : node.getChildren()){
            if(s instanceof VBox) {
                VBox t = (VBox) s;
                boolean found = false;
                for (Node n : t.getChildren()) {
                    if (n instanceof Label) {
                        if (((Label) n).getText().contains(item)) {
                            s.setManaged(true);
                            s.setVisible(true);
                            found = true;
                            break;
                        }
                    }
                }
                if (found) continue;
                t.setManaged(false);
                t.setVisible(false);
            }
        }
    }
}
