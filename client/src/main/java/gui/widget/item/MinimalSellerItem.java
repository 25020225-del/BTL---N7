package gui.widget.item;

import gui.process.CropImage;
import gui.widget.CountdownClock;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.Map;

public class MinimalSellerItem extends MinimalItem {
    protected final String DEFAULT_IMAGEURL = "https://res.cloudinary.com/de1isjzur/image/upload/v1777703968/iapj7jtzllkfggb0hvxf.jpg";
    ImageView imageView;
    Label lblName;
    Label lblType;
    Label lblStatus;
    Label lblStartingPrice;
    Label lblCurrentPrice;
    Label lblBidIncrement;
    CountdownClock clock;


    String itemId;
    String imageUrl;
    String itemName;
    String itemType;
    String status;
    long startingPrice;
    long currentPrice;
    long bidIncrement;
    long endTime;
    long highestMaxBid;

    public MinimalSellerItem(String itemId, String imageUrl, String itemName, String itemType, String status, long startingPrice, long currentPrice, long bidIncrement, long endTime) {
        super(itemId);
        this.itemId = itemId;
        this.itemName = itemName;
        this.itemType = itemType;
        this.status = status;

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

        this.startingPrice = startingPrice;
        this.currentPrice = currentPrice;
        this.bidIncrement = bidIncrement;
        this.endTime = endTime;
        this.setUserData(itemId + itemName + status);

        lblName = new Label("Name: " + itemName);
        lblType = new Label(itemType);
        lblType.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        lblStatus = new Label("Status: " + status);
        lblStartingPrice = new Label("Starting price: " + String.valueOf(startingPrice) + " VND");
        lblCurrentPrice = new Label("Current price: " + String.valueOf(currentPrice) + " VND");
        lblBidIncrement = new Label("Bid increment: " + String.valueOf(bidIncrement) + " VND");
        clock = new CountdownClock();
        clock.start(endTime);
        this.getChildren().addAll(
                imageView,
                lblName,
                lblType,
                lblStatus,
                lblStartingPrice,
                lblCurrentPrice,
                lblBidIncrement,
                clock
        );
    }

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

    public void setNewPrice(long newPrice, long endTime) {
        this.currentPrice = newPrice;
        this.endTime = endTime;
        lblCurrentPrice.setText("Current price: " + String.valueOf(currentPrice) + " VND");
        clock.start(endTime);
    }

    public void setStatus(String status) {
        this.status = status;
        lblStatus.setText("Status " + status);
    }
}
