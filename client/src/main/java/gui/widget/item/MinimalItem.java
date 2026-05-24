package gui.widget.item;

import gui.widget.IconButton;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;

/**
 * Base canonical view model element defining structural visualization rules for an auction portfolio card.
 * Establishes geometric boundaries, padding configurations, and core layout constraints
 * across generalized grid presentation viewports.
 */
public class MinimalItem extends VBox {
    protected IconButton auctionButton;

    /**
     * Allocates a structured portfolio container component bound to a unique domain identifier.
     *
     * @param id the system-wide cryptographic or unique sequence token of the target entity
     */
    public MinimalItem(String id) {
        this.setPrefSize(260, 370);
        this.setPadding(new Insets(20));
        this.setSpacing(10);
        this.setStyle(
                "-fx-background-radius: 10; " +
                        "-fx-border-style: solid; " +
                        "-fx-border-color: #c2c2c2; " +
                        "-fx-border-width: 2; " +
                        "-fx-border-radius: 10; " +
                        "-fx-background-color: white;"
        );
        this.setId(id);
    }
}