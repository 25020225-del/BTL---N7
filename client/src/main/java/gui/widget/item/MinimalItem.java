package gui.widget.item;

import client.network.NetworkService;
import gui.process.CropImage;
import gui.widget.CountdownClock;
import gui.widget.IconButton;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * A custom UI widget representing a condensed view of an auction item.
 * Displayed primarily in the Marketplace grid layout.
 */
public class MinimalItem extends VBox {
    protected final String DEFAULT_IMAGEURL = "https://res.cloudinary.com/de1isjzur/image/upload/v1777703968/iapj7jtzllkfggb0hvxf.jpg";
    protected Label nameLabel;
    protected Label priceLabel;
    protected ImageView imageView;
    protected CountdownClock countdownClock;
    protected IconButton auctionButton;

    /**
     * Constructs a minimal item card for the marketplace grid.
     *
     * @param id          The unique identifier of the auction.
     * @param nameString  The name of the item.
     * @param priceString The current price formatted as a string.
     */
    public MinimalItem(String id, String nameString, String priceString) {

        this.setPrefSize(260, 370);
        this.setPadding(new Insets(20));
        this.setSpacing(10);
        this.setId(id);
        this.nameLabel = new Label(nameString);
        this.priceLabel = new Label(priceString + " VND");
        this.getChildren().addAll(
                nameLabel,
                priceLabel
        );
    }

    public MinimalItem(String id){
        this.setPrefSize(260, 370);
        this.setPadding(new Insets(20));
        this.setSpacing(10);
        this.setId(id);
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