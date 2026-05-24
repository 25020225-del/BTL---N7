package gui.process;

import javafx.animation.Transition;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.util.Duration;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Dynamic numerical interpolation engine for JavaFX presentation elements.
 * Generates perceptual motion transformations targeting fast financial state mutations.
 */
public class PriceTweener {

    private static final NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));

    /**
     * Orchestrates a localized frame translation to smooth step-valued price modifications.
     * Enforces execution to stick strictly to the primary application UI rendering thread context.
     *
     * @param priceLabel the visual rendering component host containing the numeric string
     * @param oldPrice   the initial base scalar value state
     * @param newPrice   the terminal target scalar value state
     */
    public static void animatePriceChange(Label priceLabel, long oldPrice, long newPrice) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> animatePriceChange(priceLabel, oldPrice, newPrice));
            return;
        }

        if (priceLabel.getProperties().containsKey("priceTransition")) {
            Transition oldTransition = (Transition) priceLabel.getProperties().get("priceTransition");
            oldTransition.stop();
        }

        Transition transition = new Transition() {
            {
                setCycleDuration(Duration.millis(250));
            }

            @Override
            protected void interpolate(double frac) {
                // Non-linear cubic ease-out transformation: f(x) = 1 - (1 - x)^3.
                // Enforces a high initial velocity that dampens towards the destination terminal bounds.
                double easeOutFrac = 1 - Math.pow(1 - frac, 3);

                long currentValue = (long) (oldPrice + (newPrice - oldPrice) * easeOutFrac);
                priceLabel.setText(formatter.format(currentValue) + " đ");

                if (frac < 0.5) {
                    priceLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                } else {
                    priceLabel.setStyle("-fx-text-fill: #2c3e50; -fx-font-weight: bold;");
                }
            }
        };

        priceLabel.getProperties().put("priceTransition", transition);
        transition.play();
    }
}