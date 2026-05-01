package gui.widget;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * A custom UI widget representing a condensed view of an auction item.
 * Displayed primarily in the Marketplace grid layout.
 */
public class MinimalItem extends VBox {
    private Label nameLabel;
    private Label priceLabel;
    private CountdownClock countdownClock;
    private IconButton auctionButton;

    /**
     * Constructs a minimal item card for the marketplace grid.
     *
     * @param id         The unique identifier of the auction.
     * @param nameString The name of the item.
     * @param priceString The current price formatted as a string.
     * @param dateString The expiration timestamp in milliseconds.
     */
    public MinimalItem(String id, String nameString, String priceString, long dateString) {
        // Attach the auction ID to this Node to easily target and remove it when it expires
        this.setId(id);

        this.nameLabel = new Label(nameString);
        this.priceLabel = new Label(priceString + " VND");
        this.countdownClock = new CountdownClock();
        this.countdownClock.start(dateString);

        this.auctionButton = new IconButton("mdi2c-cart-plus", "AUCTION", "Bid on this item");

        this.setStyle(
                "-fx-background-radius: 10; " +
                        "-fx-border-style: solid; " +
                        "-fx-border-color: #c2c2c2; " +
                        "-fx-border-width: 2; " +
                        "-fx-border-radius: 10; " +
                        "-fx-background-color: white;"
        );

        this.setPrefSize(260, 370);
        this.setPadding(new Insets(20));
        this.setSpacing(10);

        this.getChildren().addAll(
                nameLabel,
                priceLabel,
                countdownClock,
                auctionButton
        );
    }

    /**
     * Retrieves the primary interaction button for this item card.
     *
     * @return The "AUCTION" button.
     */
    public IconButton getAuctionButton() {
        return auctionButton;
    }
}