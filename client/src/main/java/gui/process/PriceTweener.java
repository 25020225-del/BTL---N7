package gui.process;

import javafx.animation.Transition;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.util.Duration;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Tiện ích tạo hiệu ứng số nhảy mượt mà cho JavaFX.
 * Áp dụng thuật toán Cubic Ease-Out để số nhảy nhanh ở đầu và chậm dần về đích.
 */
public class PriceTweener {

    private static final NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));

    public static void animatePriceChange(Label priceLabel, long oldPrice, long newPrice) {
        // Bắt buộc phải chạy trên JavaFX Application Thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> animatePriceChange(priceLabel, oldPrice, newPrice));
            return;
        }

        // 1. Dọn dẹp: Nếu có một hiệu ứng cũ đang chạy dở (do giá cập nhật liên tục), ta ép dừng nó lại
        if (priceLabel.getProperties().containsKey("priceTransition")) {
            Transition oldTransition = (Transition) priceLabel.getProperties().get("priceTransition");
            oldTransition.stop();
        }

        // 2. Tạo Transition mới với thời gian 250ms (Nhỉnh hơn 200ms của Server một chút để lấp đầy độ trễ)
        Transition transition = new Transition() {
            {
                setCycleDuration(Duration.millis(250));
            }

            @Override
            protected void interpolate(double frac) {
                // Sử dụng hàm Cubic Ease-Out: 1 - (1 - x)^3
                // Giúp con số thay đổi tự nhiên hơn là chạy tuyến tính (Linear)
                double easeOutFrac = 1 - Math.pow(1 - frac, 3);

                long currentValue = (long) (oldPrice + (newPrice - oldPrice) * easeOutFrac);
                priceLabel.setText(formatter.format(currentValue) + " đ");

                // Đổi màu Label sang đỏ/xanh chớp nháy nhẹ nếu muốn thêm kịch tính (Tùy chọn)
                if (frac < 0.5) {
                    priceLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;"); // Đỏ khi đang nảy số
                } else {
                    priceLabel.setStyle("-fx-text-fill: #2c3e50; -fx-font-weight: bold;"); // Đen/Xanh đậm khi chốt
                }
            }
        };

        // 3. Gắn Transition vào Label để dễ dàng quản lý vòng đời
        priceLabel.getProperties().put("priceTransition", transition);
        transition.play();
    }
}