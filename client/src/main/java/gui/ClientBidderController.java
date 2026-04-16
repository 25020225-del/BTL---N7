package gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import model.Bidder;
import model.User;

import java.io.File;
import java.io.IOException;


public class ClientBidderController {

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


    public static void start() throws IOException {
        VBox mainDock = (VBox) MainApplication.rootMainView.lookup("#mainDock");
        VBox mainViewController = (VBox) MainApplication.rootMainView.lookup("#mainViewController");

        VBox product = (VBox) WidgetFactory.createMinimalItem("Butter","30$","12 days");
        TilePane table = null;

        for (Node node : mainViewController.getChildren()) {
            if (node instanceof ScrollPane) {
                ScrollPane sp = (ScrollPane) node;
                if (sp.getContent() instanceof TilePane) {
                    table = (TilePane) sp.getContent();
                }
            }
        }

        AnchorPane find = (AnchorPane) mainViewController.getChildren().get(0);

        Button findItem = (Button) WidgetFactory.createButton("mdi2f-file-find-outline","","Find");

        findItem.setOnAction(event -> {
            fadeNode(find,!find.isVisible());
        });

        mainDock.getChildren().addFirst(findItem);

        table.getChildren().add(WidgetFactory.createMinimalItem("Máy xay tinh trùng trí","30000","3"));
        table.getChildren().add(WidgetFactory.createMinimalItem("Đùi gà tẩm bột chiên xù","40000","3"));
        table.getChildren().add(WidgetFactory.createMinimalItem("Maáy bay đồ chơi mini","1200000","3"));
        table.getChildren().add(WidgetFactory.createMinimalItem("Thitj cừu nướng","127000","4"));
        table.getChildren().add(WidgetFactory.createMinimalItem("Mỡ lợn","80000","3"));
        table.getChildren().add(WidgetFactory.createMinimalItem("Đầu cá","35000","2"));
        System.out.println(table);
    }
}
