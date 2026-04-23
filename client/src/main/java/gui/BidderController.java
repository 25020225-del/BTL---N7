package gui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import java.io.IOException;

public class BidderController {

    @FXML private ScrollPane scrollPane;
    @FXML private FlowPane productContainer;
    @FXML private Label lblUserMenu;

    @FXML
    public void initialize() {
        // Load 15 sản phẩm đầu tiên khi mở ứng dụng
        loadMoreProducts(15);

        // Bắt sự kiện cuộn
        scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() >= 0.99) { // Nếu cuộn gần tới đáy (99%)
                loadMoreProducts(10); // Load thêm 10 cái nữa
            }
        });
        setupUserMenu();
    }

    private void loadMoreProducts(int count) {
        try {
            for (int i = 0; i < count; i++) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/ProductItem.fxml"));
                VBox item = loader.load();

                // Lấy dữ liệu giả định
                String name = "Sản phẩm " + (productContainer.getChildren().size() + 1);
                String price = "$" + (100 + i);

                // Gắn sự kiện click cho cả cái hộp sản phẩm
                item.setOnMouseClicked(event -> {
                    openDetailPopup(name, price);
                });

                productContainer.getChildren().add(item);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void openDetailPopup(String name, String price) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ProductDetail.fxml"));
            javafx.scene.Parent root = loader.load();

            // Lấy controller của cửa sổ chi tiết để truyền dữ liệu
            ItemDetailController controller = loader.getController();
            controller.setProductData(name, price);

            // Tạo một cửa sổ mới (Stage)
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setScene(new javafx.scene.Scene(root));
            stage.setTitle("Chi tiết sản phẩm: " + name);
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL); // Chặn tương tác với cửa sổ chính khi đang xem chi tiết
            stage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }
    private void setupUserMenu() {
        // 1. Tạo ContextMenu (Cái hộp nhỏ)
        ContextMenu userMenu = new ContextMenu();

        // 2. Tạo các mục trong hộp đó
        MenuItem profileItem = new MenuItem("Thông tin cá nhân");
        MenuItem logoutItem = new MenuItem("Đăng xuất");

        // Thêm CSS class nếu bạn muốn trang trí bằng file .css
        logoutItem.setStyle("-fx-text-fill: red;");

        // 3. Xử lý sự kiện khi bấm vào "Đăng xuất"
        logoutItem.setOnAction(event -> {
            System.out.println("Đang thực hiện đăng xuất...");
            // Thêm logic chuyển về màn hình Login ở đây
        });

        userMenu.getItems().addAll(profileItem, logoutItem);

        // 4. Hiển thị khi trỏ chuột vào (Hover)
        lblUserMenu.setOnMouseEntered(event -> {
            // Hiển thị menu ngay phía dưới cái Label
            userMenu.show(lblUserMenu, Side.BOTTOM, 0, 0);
        });

        // Tùy chọn: Tự động đóng menu sau vài giây nếu chuột rời đi
        userMenu.getScene().getWindow().focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) userMenu.hide();
        });
    }
}
