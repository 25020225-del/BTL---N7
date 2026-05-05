package gui.widget;

import gui.process.CropImage;
import javafx.geometry.Insets;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

/**
 * A custom UI widget representing a condensed view of an auction item.
 * Displayed primarily in the Marketplace grid layout.
 */
public class MinimalItem extends VBox {
    private Label nameLabel;
    private Label priceLabel;
    private ImageView imageView;
    private CountdownClock countdownClock;
    private IconButton auctionButton;

    /**
     * Constructs a minimal item card for the marketplace grid.
     *
     * @param id          The unique identifier of the auction.
     * @param nameString  The name of the item.
     * @param priceString The current price formatted as a string.
     * @param dateString  The expiration timestamp in milliseconds.
     */
    public MinimalItem(String id, String imageUrl, String nameString, String priceString, long dateString) {

        this.setPrefSize(260, 370);
        this.setPadding(new Insets(20));
        this.setSpacing(10);

        // Attach the auction ID to this Node to easily target and remove it when it expires
        imageView = new ImageView();

        if (imageUrl == null) {
            imageUrl = "https://res.cloudinary.com/de1isjzur/image/upload/v1777703968/iapj7jtzllkfggb0hvxf.jpg";
        }
        Image image = new Image(imageUrl, true);
        image.progressProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() == 1.0 && !image.isError()) {
                javafx.application.Platform.runLater(() -> {
                            CropImage.cropImage(imageView, image, 100, 100);
                        }
                );
            }
        });
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

    /**
     * Retrieves the primary interaction button for this item card.
     *
     * @return The "AUCTION" button.
     */
    public IconButton getAuctionButton() {
        return auctionButton;
    }
}