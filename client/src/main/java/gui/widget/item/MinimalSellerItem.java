package gui.widget.item;

import gui.process.CropImage;
import gui.widget.CountdownClock;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.Map;

/**
 * Specialized graphical display element representing an auction asset from the seller's perspective.
 * Orchestrates localized lifecycle state visualization, background image pipeline retrieval,
 * and persistent temporal synchronization clocks.
 */
public class MinimalSellerItem extends MinimalItem {

    private static final String DEFAULT_IMAGEURL = "https://res.cloudinary.com/de1isjzur/image/upload/v1777703968/iapj7jtzllkfggb0hvxf.jpg";

    private final ImageView imageView;
    private final Label lblName;
    private final Label lblType;
    private final Label lblStatus;
    private final Label lblStartingPrice;
    private final Label lblCurrentPrice;
    private final Label lblBidIncrement;
    private final CountdownClock clock;

    private long currentPrice;
    private String status;

    /**
     * Constructs a seller-managed portfolio card layout. Maps dynamic domain properties
     * and binds an asynchronous network progress listener for cover asset resizing.
     */
    public MinimalSellerItem(String itemId, String imageUrl, String itemName, String itemType, String status,
                             long startingPrice, long currentPrice, long bidIncrement, long endTime) {
        super(itemId);
        this.currentPrice = currentPrice;
        this.status = status;
        this.setUserData(itemId + itemName + status);

        this.imageView = new ImageView();
        String resolvedUrl = (imageUrl == null) ? DEFAULT_IMAGEURL : imageUrl;

        Image image = new Image(resolvedUrl, true);
        image.progressProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() == 1.0 && !image.isError()) {
                Platform.runLater(() -> CropImage.cropImage(imageView, image, 210, 210));
            }
        });

        this.lblName = new Label("Name: " + itemName);
        this.lblType = new Label(itemType);
        this.lblType.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        this.lblStatus = new Label("Status: " + status);
        this.lblStartingPrice = new Label("Starting price: " + startingPrice + " VND");
        this.lblCurrentPrice = new Label("Current price: " + currentPrice + " VND");
        this.lblBidIncrement = new Label("Bid increment: " + bidIncrement + " VND");

        this.clock = new CountdownClock();
        this.clock.start(endTime);

        this.getChildren().addAll(
                imageView, lblName, lblType, lblStatus,
                lblStartingPrice, lblCurrentPrice, lblBidIncrement, clock
        );
    }

    /**
     * Factory assembly routing routine that builds a concrete instance from a un-typed schema data map.
     *
     * @param map the relational structural wire-frame collection containing domain properties
     * @return a fully populated initialization node instance
     */
    public static MinimalSellerItem newMinimalSellerItemFromMap(Map<String, Object> map) {
        return new MinimalSellerItem(
                (String) map.get("id"),
                (String) map.get("imageUrl"),
                (String) map.get("itemName"),
                (String) map.get("itemType"),
                (String) map.get("status"),
                ((Number) map.get("startingPrice")).longValue(),
                ((Number) map.get("currentPrice")).longValue(),
                ((Number) map.get("bidIncrement")).longValue(),
                ((Number) map.get("endTime")).longValue()
        );
    }

    /**
     * Mutates the internal valuation metrics and prompts an active re-render across display nodes.
     *
     * @param newPrice the absolute increment metric representing the target current valuation
     * @param endTime  the epoch millisecond milestone recalculating countdown boundaries
     */
    public void setNewPrice(long newPrice, long endTime) {
        this.currentPrice = newPrice;
        lblCurrentPrice.setText("Current price: " + currentPrice + " VND");
        clock.start(endTime);
    }

    public void setStatus(String status) {
        this.status = status;
        lblStatus.setText("Status: " + status);
    }

    public long getCurrentPrice() {
        return currentPrice;
    }

    public String getStatus() {
        return status;
    }
}