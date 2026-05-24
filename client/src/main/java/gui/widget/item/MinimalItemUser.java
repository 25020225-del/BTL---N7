package gui.widget.item;

import gui.process.CropImage;
import gui.widget.CountdownClock;
import gui.widget.IconButton;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.function.Consumer;

public class MinimalItemUser extends MinimalItem {
    protected final String DEFAULT_IMAGEURL = "https://res.cloudinary.com/de1isjzur/image/upload/v1777703968/iapj7jtzllkfggb0hvxf.jpg";
    protected Label lblName;
    protected Label lblType;
    protected Label lblPrice;
    protected ImageView imageView;
    protected CountdownClock countdownClock;

    public MinimalItemUser(String id, String imageUrl, String nameString, String itemType, String priceString, long dateString) {
        super(id);
        this.setUserData(id + nameString + priceString + dateString);
        this.getProperties().put("itemType", itemType);

        imageView = new ImageView();

        if (imageUrl == null) {
            imageUrl = DEFAULT_IMAGEURL;
        }
        Image image = new Image(imageUrl, true);
        image.progressProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() == 1.0 && !image.isError()) {
                javafx.application.Platform.runLater(() -> {
                            CropImage.cropImage(imageView, image, 210, 210);
                        }
                );
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
     * Retrieves the primary interaction button for this item card.
     *
     * @return The "AUCTION" button.
     */
    public IconButton getAuctionButton() {
        return auctionButton;
    }
}
