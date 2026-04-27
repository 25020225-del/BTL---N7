package gui.widget;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class MinimalItem extends VBox {
    private Label nameLabel;
    private Label priceLabel;
    private CountdownClock countdownClock;
    private IconButton auctionButton;

    public MinimalItem(String nameString, String priceString, long dateString) {
        // 1. Khởi tạo các thành phần con
        this.nameLabel = new Label(nameString);
        this.priceLabel = new Label(priceString + " VND");
        this.countdownClock = new CountdownClock();
        this.countdownClock.start(dateString);

        // Sử dụng class IconButton bạn vừa refactor xong
        this.auctionButton = new IconButton("mdi2c-cart-plus", "AUCTION", "Bid on this item");

        // 2. Thiết lập Style cho VBox (chính là class này)
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

    // Bạn có thể thêm getter để xử lý sự kiện bên ngoài nếu cần
    public IconButton getAuctionButton() {
        return auctionButton;
    }
}