package gui.userController;

import client.handler.AuctionEventBus;
import client.network.NetworkService;
import client.service.AuctionService;
import gui.MainApplication;
import gui.process.Convert;
import gui.process.EditAuction;
import gui.process.AlertHelper;
import gui.process.CropImage;
import gui.widget.CountdownClock;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import model.auction.Auction;
import model.user.User;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.TimeUtil;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Controller for the Auction Item Detail View.
 * Handles countdown synchronization, UI population, manual bidding,
 * and rendering the real-time bid history line chart.
 */
public class ItemDetailController{
    private static final Logger log = LoggerFactory.getLogger(ItemDetailController.class);
    private User currentUser;

    private final String DEFAULT_IMAGEURL = "https://res.cloudinary.com/de1isjzur/image/upload/v1777703968/iapj7jtzllkfggb0hvxf.jpg";

    private Parent detailView;

    @FXML private HBox hbTime;
    @FXML private ImageView imgLarge;
    @FXML private Label lblDetailTitle;
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblLeader;
    @FXML private TextField txtBidAmount;
    @FXML private Button btnPlaceBid;
    @FXML private TextArea txtDescription;

    @FXML private TextField txtMaxBid;
    @FXML private TextField txtBidIncrement;

    @FXML private VBox vbBidHandle;
    @FXML private VBox vbAuctionControl;

    private CountdownClock lblTimeLeft = new CountdownClock();

    // --- Chart Components ---
    @FXML private LineChart<String, Number> bidHistoryChart;
    @FXML private CategoryAxis xAxisTime;
    @FXML private NumberAxis yAxisPrice;

    private XYChart.Series<String, Number> priceSeries;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Timeline timeline;
    private String currentAuctionId;
    private long endTimeMillis;
    private PropertyChangeListener priceUpdateListener;

    public ItemDetailController(User currentUser) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/Productdetail.fxml"));
        this.currentUser = currentUser;
        loader.setController(this);
        try {
            detailView = loader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        hbTime.getChildren().add(lblTimeLeft);
    }

    public Parent getParent() {
        return detailView;
    }

    @FXML
    public void initialize() {
        System.out.println("[System]: Item Detail View Initialized.");
        setupChart();

        // Subscribe to the global Event Bus for real-time price updates
        priceUpdateListener = evt -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) evt.getNewValue();
            String auctionId = (String) data.get("auctionId");
            // Only update the UI if the event belongs to the currently viewed auction
            if (this.currentAuctionId != null && this.currentAuctionId.equals(auctionId)) {
                long newPrice = ((Number) data.get("newPrice")).longValue();
                String winnerName = (String) data.get("winnerName");
                long newEndTimeStr = ((Number) data.get("newEndTime")).longValue();
                endTimeMillis = newEndTimeStr;
                lblTimeLeft.start(endTimeMillis);
                // Ensure UI modifications happen on the JavaFX Application Thread
                Platform.runLater(() -> {
                    updateRealTimePrice(newPrice, winnerName);
                });
            }
        };
        AuctionEventBus.addListener(AuctionEventBus.PRICE_UPDATED, priceUpdateListener);
        AuctionEventBus.addListener(AuctionEventBus.FETCH_TRANSACTIONS_SUCCESS, evt -> {
            NetworkMessage response =  (NetworkMessage) evt.getNewValue();
            List<Map<String, Object>> responseData = (List<Map<String, Object>>) response.getData();
            setTransActionHistoryData(responseData);
        });
    }

    /**
     * Cleans up resources, listeners, and timers to prevent memory leaks.
     */
    public void dispose() {
        if (priceUpdateListener != null) {
            AuctionEventBus.removeListener(AuctionEventBus.PRICE_UPDATED, priceUpdateListener);
            AuctionEventBus.removeAllListeners(AuctionEventBus.FETCH_TRANSACTIONS_SUCCESS);
        }
        if (timeline != null) {
            timeline.stop();
        }
        log.info("[system]: Item Detail View Disposed.");
    }

    /**
     * Initializes the LineChart structure for tracking bid history.
     */
    private void setupChart() {
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Bid Price");
        bidHistoryChart.getData().add(priceSeries);

        // Optimize chart performance for real-time updates
        bidHistoryChart.setCreateSymbols(true);
    }

    /**
     * Populates the UI with detailed auction data using the Domain Model.
     * This ensures strict MVC compliance by avoiding raw Map parsing.
     *
     * @param auction The Auction domain object.
     */
    public void setAuctionData(Auction auction) {
        this.currentAuctionId = auction.getId();

        if (auction.getSeller().getId().equals(currentUser.getId())) {
            vbBidHandle.setVisible(false);
            vbBidHandle.setManaged(false);
        }
        else{
            vbAuctionControl.setVisible(false);
            vbAuctionControl.setManaged(false);
        }

        // Use getters for accurate Domain Model access
        String name = auction.getItem().getItemName();
        String desc = auction.getItem().getDescription();
        String imageUrl = auction.getItem().getImageUrl();
        long startPrice = auction.getItem().getStartingPrice();
        long currentPrice = auction.getCurrentPrice();
        this.endTimeMillis = auction.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        if (imageUrl == null) {
            imageUrl = DEFAULT_IMAGEURL;
        }
        Image image = new Image(imageUrl, true);
        image.progressProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.doubleValue() == 1.0 && !image.isError()) {
                Platform.runLater(() -> {
                    CropImage.cropImage(imgLarge, image, 720, 480);
                });
            }
        });

        String leader = (auction.getWinningBidder() != null) ? auction.getWinningBidder().getUserName() : "None";

        // Set UI text
        lblDetailTitle.setText(name);
        txtDescription.setText(desc);
        lblStartPrice.setText(String.format("%,d VND", startPrice));
        lblCurrentPrice.setText(String.format("%,d VND", currentPrice));
        lblLeader.setText(leader);

        // Add the initial starting point to the chart
        addPointToChart(startPrice, LocalDateTime.now());


        startCountdown();
    }

    public void setTransActionHistoryData(List<Map<String, Object>> transActionHistoryData) {
        for(Map<String, Object> map : transActionHistoryData){
            Number amountNum = (Number) map.get("bid_amount");
            Long amount = amountNum.longValue();
            String bidTime = (String) map.get("bid_time");
            LocalDateTime time = LocalDateTime.parse(bidTime);
            addPointToChart(amount, time);
        }
    }

    /**
     * Triggered internally by the Event Bus listener when a new bid is placed.
     * Updates the local UI dynamically.
     *
     * @param newPrice   The newly established price.
     * @param winnerName The username of the user who placed the bid.
     */
    private void updateRealTimePrice(long newPrice, String winnerName) {
        lblCurrentPrice.setText(String.format("%d VND", newPrice));
        lblLeader.setText(winnerName);

        // Add a new dynamic node to the line chart
        addPointToChart(newPrice, Convert.longToTimestamp(System.currentTimeMillis()));

        // Add an engaging visual highlight effect to the price label
        gui.process.AnimateEffect.highlightText(lblCurrentPrice);
    }

    /**
     * Adds a new data point to the active LineChart.
     */
    private void addPointToChart(long price, LocalDateTime time) {
        String currentTimeStr = time.format(timeFormatter);
        XYChart.Data<String, Number> newPoint = new XYChart.Data<>(currentTimeStr, price);
        priceSeries.getData().add(newPoint);

        // Optional: Keep chart clean by removing old nodes if there are too many (e.g., > 20)
        if (priceSeries.getData().size() > 20) {
            priceSeries.getData().removeFirst();
        }
    }

    /**
     * Handles the manual bid placement logic.
     */
    @FXML
    private void handlePlaceBid() {
        vbBidHandle.setDisable(true);
        PauseTransition pauseTransition = new PauseTransition(Duration.seconds(2));
        pauseTransition.setOnFinished(event -> {vbBidHandle.setDisable(false);});
        pauseTransition.play();
        try {
            long bidAmount = Long.parseLong(txtBidAmount.getText().replace(",", ""));

            if (bidAmount <= 0) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, "Error", "Amount must be greater than 0");
                return;
            }

            AuctionService.placeBid(currentAuctionId, bidAmount);

        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Error", "Invalid amount format");
        }
    }

    @FXML
    private void handleAutoBid() {
        // 1. Vô hiệu hóa nút bấm tạm thời để tránh spam
        vbBidHandle.setDisable(true);
        PauseTransition pauseTransition = new PauseTransition(Duration.seconds(2));
        pauseTransition.setOnFinished(event -> { vbBidHandle.setDisable(false); });
        pauseTransition.play();

        try {
            // 2. Lấy và kiểm tra dữ liệu từ giao diện
            String maxBidStr = txtMaxBid.getText().replace(",", "").trim();
            String incrementStr = txtBidIncrement.getText().replace(",", "").trim();

            if (maxBidStr.isEmpty() || incrementStr.isEmpty()) {
                AlertHelper.showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Vui lòng nhập giá tối đa và bước nhảy.");
                return;
            }

            long maxBid = Long.parseLong(maxBidStr);
            long bidIncrement = Long.parseLong(incrementStr);

            if (maxBid <= 0 || bidIncrement <= 0) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, "Lỗi", "Số tiền phải lớn hơn 0.");
                return;
            }

            AuctionService.setAutoBid(currentAuctionId, maxBid, bidIncrement);

            log.info("Đã gửi yêu cầu thiết lập AutoBid cho đấu giá: {}", currentAuctionId);
            AlertHelper.showAlert(Alert.AlertType.INFORMATION, "Thông báo", "Đã gửi yêu cầu kích hoạt đặt giá tự động.");

        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Lỗi định dạng", "Vui lòng chỉ nhập số hợp lệ.");
        }
    }

    @FXML
    private void handleEditAuction() {
        EditAuction.edit(currentAuctionId);
    }

    @FXML
    private void handleDeleteAuction() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText("Delete Auction?");
        confirm.setContentText("Are you sure you want to delete auction: " + currentAuctionId + "?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                AuctionService.deleteAuction(currentAuctionId);
            }
        });
    }

    /**
     * Synchronizes the UI countdown timer with the auction's remaining duration.
     */
    private void startCountdown() {
        lblTimeLeft.start(endTimeMillis);
    }
}
