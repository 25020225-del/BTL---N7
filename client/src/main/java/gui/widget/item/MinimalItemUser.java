package gui.widget.item;

import gui.process.CropImage;
import gui.widget.CountdownClock;
import gui.widget.IconButton;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * Public-facing interactive auction listing card. Integrates background asset streaming listeners,
 * active temporal synchronization metrics, and discrete interactive engagement components.
 */
public class MinimalItemUser extends MinimalItem {
    protected final String DEFAULT_IMAGEURL = "https://res.cloudinary.com/de1isjzur/image/upload/v1777703968/iapj7jtzllkfggb0hvxf.jpg";
    protected Label lblName;
    protected Label lblType;
    protected Label lblPrice;
    protected ImageView imageView;
    protected CountdownClock countdownClock;

    /**
     * Synthesizes an interactive asset component and attaches an asynchronous streaming listener
     * onto the inbound payload image properties block.
     *
     * @param id          the primary system execution mapping key
     * @param imageUrl    the localized or remote URI pointer hosting the visual graphic binary asset
     * @param nameString  the literal alphanumeric identification text of the portfolio element
     * @param itemType    the categorical domain taxonomy assignment
     * @param priceString the formatted monetary numerical display string
     * @param dateString  the epoch millisecond boundary controlling session termination steps
     */
    public MinimalItemUser(String id, String imageUrl, String nameString, String itemType, String priceString, long dateString) {
        super(id);
        this.setUserData(id + nameString + priceString + dateString);
        this.getProperties().put("itemType", itemType);

        imageView = new ImageView();
        String resolvedUrl = (imageUrl == null) ? DEFAULT_IMAGEURL : imageUrl;

        Image image = new Image(resolvedUrl, true);
        image.progressProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() == 1.0 && !image.isError()) {
                Platform.runLater(() -> CropImage.cropImage(imageView, image, 210, 210));
            }
        });

        this.lblName = new Label(nameString);
        this.lblType = new Label(itemType);
        this.lblType.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        this.lblPrice = new Label(priceString + " VND");
        this.countdownClock = new CountdownClock();
        this.countdownClock.start(dateString);

        this.auctionButton = new IconButton("mdi2c-cart-plus", "AUCTION", "Bid on this item");

        this.getChildren().addAll(
                imageView,
                lblName,
                lblType,
                lblPrice,
                countdownClock,
                auctionButton
        );
    }

    /**
     * Resolves the authoritative interactive routing component linked to this representation node context.
     *
     * @return the upstream action relay button context
     */
    public IconButton getAuctionButton() {
        return auctionButton;
    }
}