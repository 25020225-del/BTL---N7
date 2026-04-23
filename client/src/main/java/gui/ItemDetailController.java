package gui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

public class ItemDetailController {
    // 1. Khai báo các thành phần UI (Giữ nguyên các biến cũ của bạn)
    @FXML private ImageView imgLarge;
    @FXML private Label lblDetailTitle;
    @FXML private Label lblDetailPrice;
    @FXML private Label lblTimeLeft;
    @FXML private TextField txtBidAmount;
    @FXML private TextArea txtDescription;

    // 2. Các biến phục vụ logic đồng hồ
    private int totalSeconds = 3600; // Giả sử 1 giờ
    private Timeline timeline;

    // 3. Hàm khởi tạo (Chạy tự động khi mở Popup)
    @FXML
    public void initialize() {
        System.out.println("Màn hình chi tiết đã sẵn sàng!");
        startCountdown();
    }

    // 4. Hàm nhận dữ liệu từ trang chủ (Giữ lại hàm này để MainController gọi)
    public void setProductData(String name, String price) {
        lblDetailTitle.setText(name);
        lblDetailPrice.setText(price);
    }

    // 5. Logic đếm ngược (Dán thêm phần này)
    private void startCountdown() {
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            totalSeconds--;

            int hours = totalSeconds / 3600;
            int minutes = (totalSeconds % 3600) / 60;
            int seconds = totalSeconds % 60;

            lblTimeLeft.setText(String.format("Thời gian còn lại: %02d:%02d:%02d", hours, minutes, seconds));

            if (totalSeconds <= 0) {
                timeline.stop();
                lblTimeLeft.setText("HẾT GIỜ!");
                lblTimeLeft.setStyle("-fx-text-fill: red;");
            }
        }));

        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }
}