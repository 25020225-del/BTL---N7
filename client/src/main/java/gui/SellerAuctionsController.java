package gui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SellerAuctionsController {

    //BIẾN STATIC LƯU SẢN PHẨM ĐƯỢC CHỌN ĐỂ TRUYỀN GIỮA 2 MÀN HÌNH
    // Thay 'Object' bằng Class Model thực tế (Product hoặc Auction)
    private static Object selectedProductData = null;


    // 1. KHAI BÁO BIẾN CHO TRANG DANH SÁCH (SellerAuctionsView.fxml)

    @FXML private TableView<Object> tableSellerAuctions;
    @FXML private TableColumn<Object, String> colAuctionId;
    @FXML private TableColumn<Object, String> colItemName;
    @FXML private TableColumn<Object, Double> colStartPrice;
    @FXML private TableColumn<Object, Double> colCurrentPrice;
    @FXML private TableColumn<Object, String> colTimeRemaining;
    @FXML private TableColumn<Object, String> colStatus;
    @FXML private Button btnViewDetails;


    // 2. KHAI BÁO BIẾN CHO TRANG CHI TIẾT (ProductDetail_Seller.fxml)

    @FXML private ImageView imgLarge;
    @FXML private Label lblDetailTitle;
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblLeader;
    @FXML private Label lblRemainingTime;
    @FXML private TextField txtExtendTime;
    @FXML private TextArea txtDescription;
    @FXML private Button btnEditAuction;
    @FXML private Button btnRemoveAuction;

    // Các thành phần của Đồ thị (CHỈ KHAI BÁO Ở ĐÂY, KHÔNG KHAI BÁO LẠI TRONG HÀM)
    @FXML private LineChart<String, Number> bidHistoryChart;
    @FXML private CategoryAxis xAxisTime;
    @FXML private NumberAxis yAxisPrice;


    // 3. HÀM KHỞI TẠO TỰ ĐỘNG (Phân tách thông minh cho cả 2 View)

    @FXML
    public void initialize() {
        // Nếu TableView khác null -> Đang ở màn hình Danh sách
        if (tableSellerAuctions != null) {
            initListView();
        }

        // Nếu nhãn Tiêu đề khác null -> Đang ở màn hình Chi tiết Seller
        if (lblDetailTitle != null) {
            initDetailView();
        }
    }


    // 4. LOGIC XỬ LÝ TRANG DANH SÁCH

    private void initListView() {
        colAuctionId.setCellValueFactory(new PropertyValueFactory<>("auctionId"));
        colItemName.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colStartPrice.setCellValueFactory(new PropertyValueFactory<>("startPrice"));
        colCurrentPrice.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        colTimeRemaining.setCellValueFactory(new PropertyValueFactory<>("timeRemaining"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Đúp chuột (Double click) vào dòng bất kỳ để mở nhanh chi tiết
        tableSellerAuctions.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && tableSellerAuctions.getSelectionModel().getSelectedItem() != null) {
                switchSceneToDetail(tableSellerAuctions.getSelectionModel().getSelectedItem());
            }
        });

        loadDataFromServer();
    }

    @FXML
    private void handleViewDetails(ActionEvent event) {
        Object selected = tableSellerAuctions.getSelectionModel().getSelectedItem();
        if (selected != null) {
            switchSceneToDetail(selected);
        } else {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng chọn một sản phẩm từ danh sách!");
        }
    }

    private void switchSceneToDetail(Object product) {
        selectedProductData = product;
        changeScene("ProductDetail_Seller.fxml");
    }


    // 5. LOGIC XỬ LÝ TRANG CHI TIẾT & ĐỒ THỊ

    private void initDetailView() {
        if (selectedProductData == null) return;

        // Ép kiểu (Cast) dữ liệu sang Model của bạn ở đây nếu cần thiết
        // Ví dụ: Product prod = (Product) selectedProductData;

        // Đổ dữ liệu text tổng quan
        lblDetailTitle.setText("Tên Sản Phẩm Đang Đấu Giá");
        lblCurrentPrice.setText("1,500,000 VND");
        lblLeader.setText("User_KinhDoanh99");
        lblRemainingTime.setText("01:24:45");
        txtDescription.setText("Mô tả sản phẩm được load động tại đây...");

        // GỌI HÀM VẼ ĐỒ THỊ: Mock dữ liệu chạy thử ban đầu
        List<BidDto> demoHistory = new ArrayList<>();
        demoHistory.add(new BidDto("10:00", 1000000));
        demoHistory.add(new BidDto("10:15", 1200000));
        demoHistory.add(new BidDto("10:30", 1500000));
        updateBidChart(demoHistory);
    }

    // Hàm cập nhật biểu đồ (Logic đồng bộ cấu trúc với Buyer)
    public void updateBidChart(List<BidDto> bidHistory) {
        // 1. Xóa sạch các đường vẽ cũ (nếu có)
        bidHistoryChart.getData().clear();

        // 2. Tạo một đường truyền dữ liệu mới
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Mức giá đặt");

        // 3. Đổ các mốc điểm (Thời gian, Giá tiền) vào đường truyền
        for (BidDto bid : bidHistory) {
            series.getData().add(new XYChart.Data<>(bid.getBidTime(), bid.getAmount()));
        }

        // 4. Vẽ lên đồ thị giao diện
        bidHistoryChart.getData().add(series);
    }

    @FXML
    private void handleUpdateTime(ActionEvent event) {
        String input = txtExtendTime.getText().trim();
        if (input.isEmpty() || !input.matches("\\d+")) {
            showAlert(Alert.AlertType.ERROR, "Lỗi nhập liệu", "Vui lòng nhập số phút gia hạn hợp lệ!");
            return;
        }

        // Viết Logic gửi lệnh cập nhật thời gian lên Server tại đây...
        showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đã gia hạn phiên đấu giá thêm " + input + " phút!");
        txtExtendTime.clear();
    }

    @FXML
    private void handleBackToList(ActionEvent event) {
        changeScene("SellerAuctionsView.fxml");
    }

    @FXML
    private void handleEditAuction(ActionEvent event) {
        // Logic chỉnh sửa thông tin bài đăng
    }

    @FXML
    private void handleDeleteAuction(ActionEvent event) {
        // Logic hủy hoặc đóng phiên đấu giá sớm
    }


    // 6. CÁC HÀM BỔ TRỢ HỆ THỐNG (Helper Methods)

    private void loadDataFromServer() {
        // Viết logic gọi sang Client/Server để lấy danh sách đổ vào TableView tại đây
    }

    private void changeScene(String fxmlFile) {
        try {
            // Lấy Stage hiện tại thông qua linh kiện bất kỳ đang hiển thị
            Stage stage = (Stage) (tableSellerAuctions != null ?
                    tableSellerAuctions.getScene().getWindow() : lblDetailTitle.getScene().getWindow());

            Parent root = FXMLLoader.load(getClass().getResource(fxmlFile));
            stage.setScene(new Scene(root));
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi hệ thống", "Không thể chuyển sang màn hình: " + fxmlFile);
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type, content, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
// 7. CLASS DTO ĐẠI DIỆN DỮ LIỆU ĐẶT GIÁ (Dùng vẽ đồ thị)
class BidDto {
    private String bidTime; // Trục X
    private double amount;  // Trục Y

    public BidDto(String bidTime, double amount) {
        this.bidTime = bidTime;
        this.amount = amount;
    }

    public String getBidTime() { return bidTime; }
    public double getAmount() { return amount; }
}