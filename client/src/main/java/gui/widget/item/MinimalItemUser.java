package gui.widget.item;

import gui.process.CropImage;
import gui.widget.CountdownClock;
import gui.widget.IconButton;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class MinimalItemUser extends MinimalItem {

    public MinimalItemUser(String id, String imageUrl, String nameString, String priceString, long dateString) {
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

        this.getChildren().addAll(
                imageView,
                nameLabel,
                priceLabel,
                countdownClock,
                auctionButton
        );
    }

    /**
     * Adds seller-specific management options (Edit/Delete) to this item.
     * These options are presented via a right-click context menu.
     */
    public void addSellerOptions(java.util.function.Consumer<String> onEdit, java.util.function.Consumer<String> onDelete) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem editItem = new MenuItem("Edit Auction");
        editItem.setOnAction(e -> onEdit.accept(this.getId()));

        MenuItem deleteItem = new MenuItem("Delete Auction");
        deleteItem.setStyle("-fx-text-fill: red;");
        deleteItem.setOnAction(e -> onDelete.accept(this.getId()));

        contextMenu.getItems().addAll(editItem, deleteItem);
        this.setOnContextMenuRequested(e -> contextMenu.show(this, e.getScreenX(), e.getScreenY()));
    }
}
