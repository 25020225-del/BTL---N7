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
     * Adds seller-specific management options (Edit/Delete) to this item.
     * These options are presented via a right-click context menu.
     */
    public void addSellerOptions(Consumer<String> onEdit, Consumer<String> onDelete) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem editItem = new MenuItem("Edit Auction");
        editItem.setOnAction(e -> onEdit.accept(this.getId()));

        MenuItem deleteItem = new MenuItem("Delete Auction");
        deleteItem.setStyle("-fx-text-fill: red;");
        deleteItem.setOnAction(e -> onDelete.accept(this.getId()));

        contextMenu.getItems().addAll(editItem, deleteItem);
        this.setOnContextMenuRequested(e -> contextMenu.show(this, e.getScreenX(), e.getScreenY()));
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
