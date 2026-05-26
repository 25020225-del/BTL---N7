package gui.widget;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

/**
 * High-performance application initialization rendering view. Orchestrates structured
 * parallel entry animations, vector matrix transforms, and sequential transparency fades
 * to reflect localized bootstrapping phases.
 */
public class SplashScreen extends StackPane {

    private static final String FONT_FAMILY = "-fx-font-family: 'Segoe UI';";

    private final Timeline rotateAnimation;
    private final SequentialTransition textSequenceAnimation;

    /**
     * Constructs the splash component graph hierarchy, allocates vector boundaries,
     * and maps synchronized execution paths for structural layout elements.
     */
    public SplashScreen() {
        setStyle("-fx-background-color: #f8fafc;");
        setPrefSize(800, 600);

        VBox mainContainer = new VBox(0);
        mainContainer.setAlignment(Pos.CENTER);
        mainContainer.setMaxWidth(350);

        // ── KHU VỰC LOGO ───────────────────────────────────────────────────────
        StackPane logoContainer = new StackPane();
        logoContainer.setPrefSize(100, 100);
        logoContainer.setMaxSize(100, 100);
        VBox.setMargin(logoContainer, new Insets(0, 0, 28, 0));

        Rectangle logoCard = new Rectangle(68, 68);
        logoCard.setArcWidth(24);
        logoCard.setArcHeight(24);
        logoCard.setFill(Color.web("#1e3a8a"));
        logoCard.setOpacity(0);

        Label brandLabel = new Label("N7");
        brandLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: 900; -fx-text-fill: white;" + FONT_FAMILY);
        brandLabel.setOpacity(0);

        Circle trackCircle = new Circle(44);
        trackCircle.setFill(null);
        trackCircle.setStroke(Color.web("#e2e8f0"));
        trackCircle.setStrokeWidth(1.5);

        Arc loadingArc = new Arc(50, 50, 44, 44, 90, 90);
        loadingArc.setType(ArcType.OPEN);
        loadingArc.setFill(null);
        loadingArc.setStroke(Color.web("#3b82f6"));
        loadingArc.setStrokeWidth(3);
        loadingArc.setStrokeLineCap(StrokeLineCap.ROUND);
        loadingArc.setSmooth(true);
        loadingArc.setCache(false);

        Pane spinnerWrapper = new Pane(loadingArc);
        spinnerWrapper.setPrefSize(100, 100);
        spinnerWrapper.setPickOnBounds(false);

        logoContainer.getChildren().addAll(trackCircle, logoCard, brandLabel, spinnerWrapper);

        // ── TIÊU ĐỀ HỆ THỐNG ────────────────────────────────────────────────────
        Label systemTitle = new Label("N7 Auction System");
        systemTitle.setStyle("-fx-font-size: 22px; -fx-font-weight: 800; -fx-text-fill: #0f172a;" + FONT_FAMILY);
        systemTitle.setOpacity(0);
        VBox.setMargin(systemTitle, new Insets(0, 0, 24, 0));

        // ── FOOTER META ────────────────────────────────────────────────────────
        Label metaLabel = new Label("v1.0.0  ·  N7 Group Project");
        metaLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8; -fx-font-weight: 600;" + FONT_FAMILY);
        metaLabel.setOpacity(0);

        // Nạp tất cả linh kiện vào Container chính (Đã loại bỏ stepsWrapper)
        mainContainer.getChildren().addAll(logoContainer, systemTitle, metaLabel);
        getChildren().add(mainContainer);

        // ── HOẠT ẢNH XOAY VÒNG (SPINNER ANIMATION) ────────────────────────────────
        Rotate rotateTransform = new Rotate(0, 50, 50);
        loadingArc.getTransforms().add(rotateTransform);

        rotateAnimation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(rotateTransform.angleProperty(), 0, Interpolator.LINEAR)),
                new KeyFrame(Duration.millis(950), new KeyValue(rotateTransform.angleProperty(), 360, Interpolator.LINEAR))
        );
        rotateAnimation.setCycleCount(Animation.INDEFINITE);

        // ── HOẠT ẢNH HIỆN TỪNG THÀNH PHẦN (FADE IN TIMELINE) ─────────────────────
        textSequenceAnimation = new SequentialTransition(
                new PauseTransition(Duration.millis(150)),
                createFadeTransition(logoCard, 350),
                createFadeTransition(brandLabel, 350),
                new PauseTransition(Duration.millis(150)),
                createFadeTransition(systemTitle, 400),
                new PauseTransition(Duration.millis(400)), // Tạo khoảng nghỉ tinh tế thay cho luồng text cũ
                createFadeTransition(metaLabel, 350)
        );
    }

    private FadeTransition createFadeTransition(javafx.scene.Node node, double ms) {
        FadeTransition fade = new FadeTransition(Duration.millis(ms), node);
        fade.setToValue(1);
        return fade;
    }

    /**
     * Coordinates and triggers execution of parallel entry layout sequences and vector rotations.
     */
    public void play() {
        rotateAnimation.play();
        textSequenceAnimation.play();
    }

    /**
     * Terminates the ongoing rotational frame computation loop running on the graphical component scene tree.
     */
    public void stopRotation() {
        rotateAnimation.stop();
    }
}