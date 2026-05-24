package gui.process;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Procedural layout rendering companion engine. Orchestrates graphical calculations,
 * interpolation modifications, and transition behaviors targeting scene graph active nodes.
 */
public class AnimateEffect {

    /**
     * Triggers a cyclical sequence of rapid alpha-opacity changes to highlight immediate data pipeline events.
     *
     * @param node the graphical scene element receiving attention targeting updates
     */
    public static void highlightText(Node node) {
        FadeTransition ft = new FadeTransition(Duration.millis(150), node);
        ft.setFromValue(1.0);
        ft.setToValue(0.3);
        ft.setCycleCount(4);
        ft.setAutoReverse(true);
        ft.play();
    }

    /**
     * Synchronizes structural entry vectors mapping parallel layout translations with progressive alpha modifications.
     *
     * @param node the target layout segment entering presentation viewport areas
     */
    public static void applyFadeAndTranslate(Node node) {
        Duration duration = Duration.millis(600);

        FadeTransition fadeIn = new FadeTransition(duration, node);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        TranslateTransition moveUp = new TranslateTransition(duration, node);
        moveUp.setFromY(50);
        moveUp.setToY(0);

        fadeIn.setInterpolator(Interpolator.EASE_OUT);
        moveUp.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition parallel = new ParallelTransition(fadeIn, moveUp);
        parallel.play();
    }

    /**
     * Intercepts node interactiveness and restricts interaction paths for a fixed timeline duration.
     */
    public static void pauseNode(Node node, int timeSecond) {
        node.setDisable(true);
        PauseTransition pauseTransition = new PauseTransition(Duration.seconds(timeSecond));
        pauseTransition.setOnFinished(event -> node.setDisable(false));
        pauseTransition.play();
    }

    /**
     * Modifies rendering node configuration attributes to omit targeted elements from spatial layouts entirely.
     */
    public static void hideNode(Node node) {
        node.setDisable(true);
        node.setVisible(false);
        node.setManaged(false);
    }

    /**
     * Reintroduces layout parameters back to active scene graph trees to display dormant components.
     */
    public static void showNode(Node node) {
        node.setDisable(false);
        node.setVisible(true);
        node.setManaged(true);
    }
}