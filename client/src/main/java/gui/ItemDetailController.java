package gui;

import client.handler.AuctionEventBus;
import gui.process.AlertHelper;
import gui.process.CropImage;
import gui.process.ImageUtil;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;
import model.auction.Auction;
import utils.TimeUtil;

import java.beans.PropertyChangeListener;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Controller for the Auction Item Detail View.
 * Handles countdown synchronization, UI population, manual bidding,
 * and rendering the real-time bid history line chart.
 */
public class ItemDetailController {

    @FXML
    private ImageView imgLarge;
    @FXML
    private Label lblDetailTitle;
    @FXML
    private Label lblStartPrice;
    @FXML
    private Label lblCurrentPrice;
    @FXML
    private Label lblLeader;
    @FXML
    private Label lblTimeLeft;
    @FXML
    private TextField txtBidAmount;
    @FXML
    private Button btnPlaceBid;
    @FXML
    private TextArea txtDescription;

    // --- Chart Components ---
    @FXML
    private LineChart<String, Number> bidHistoryChart;
    @FXML
    private CategoryAxis xAxisTime;
    @FXML
    private NumberAxis yAxisPrice;
    private XYChart.Series<String, Number> priceSeries;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Timeline timeline;
    private String currentAuctionId;
    private long endTimeMillis;
    private PropertyChangeListener priceUpdateListener;

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

                // Ensure UI modifications happen on the JavaFX Application Thread
                Platform.runLater(() -> {
                    updateRealTimePrice(newPrice, winnerName);
                });
            }
        };

        AuctionEventBus.addListener(AuctionEventBus.PRICE_UPDATED, priceUpdateListener);
    }

    /**
     * Cleans up resources, listeners, and timers to prevent memory leaks.
     */
    public void dispose() {
        if (priceUpdateListener != null) {
            AuctionEventBus.removeListener(AuctionEventBus.PRICE_UPDATED, priceUpdateListener);
        }
        if (timeline != null) {
            timeline.stop();
        }
        System.out.println("[System]: Item Detail Controller Disposed.");
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
        bidHistoryChart.setAnimated(true);
    }

    /**
     * Populates the UI with detailed auction data using the Domain Model.
     * This ensures strict MVC compliance by avoiding raw Map parsing.
     *
     * @param auction The Auction domain object.
     */
    public void setAuctionData(Auction auction) {
        this.currentAuctionId = auction.getId();

        // Use getters for accurate Domain Model access
        String name = auction.getItem().getItemName();
        String desc = auction.getItem().getDescription();
        String imageUrl = auction.getItem().getImageUrl();
        long startPrice = auction.getItem().getStartingPrice();
        long currentPrice = auction.getCurrentPrice();
        this.endTimeMillis = auction.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        if (imageUrl == null) {
            imageUrl = "https://res.cloudinary.com/de1isjzur/image/upload/v1777703968/iapj7jtzllkfggb0hvxf.jpg";
        }
        Image image = new Image(imageUrl, true);
        image.progressProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.doubleValue() == 1.0 && !image.isError()) {
                Platform.runLater(() -> {
                    CropImage.cropImage(imgLarge, image, 300, 300);
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
        addPointToChart(startPrice);

        // Configure Bidding Button Action
        btnPlaceBid.setOnAction(e -> handlePlaceBid());

        startCountdown();
    }

    /**
     * Triggered internally by the Event Bus listener when a new bid is placed.
     * Updates the local UI dynamically.
     *
     * @param newPrice   The newly established price.
     * @param winnerName The username of the user who placed the bid.
     */
    private void updateRealTimePrice(long newPrice, String winnerName) {
        lblCurrentPrice.setText(String.format("%,.0f VND", newPrice));
        lblLeader.setText(winnerName);

        // Add a new dynamic node to the line chart
        addPointToChart(newPrice);

        // Add an engaging visual highlight effect to the price label
        gui.process.AnimateEffect.highlightText(lblCurrentPrice);
    }

    /**
     * Adds a new data point to the active LineChart.
     */
    private void addPointToChart(long price) {
        String currentTimeStr = LocalDateTime.now().format(timeFormatter);
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
    private void handlePlaceBid() {
        try {
            long bidAmount = Long.parseLong(txtBidAmount.getText().replace(",", ""));

            if (bidAmount <= 0) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, "Error", "Amount must be greater than 0");
                return;
            }

            // Send bidding request to server
            Map<String, Object> bidData = Map.of(
                "auctionId", currentAuctionId,
                "bidAmount", bidAmount
            );
            MainApplication.networkClient.sendMessage("PLACE_BID", bidData);

        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Error", "Invalid amount format");
        }
    }

    /**
     * Synchronizes the UI countdown timer with the auction's remaining duration.
     */
    private void startCountdown() {
        if (timeline != null) timeline.stop();

        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long remaining = endTimeMillis - TimeUtil.getCurrentServerTime();
            if (remaining <= 0) {
                lblTimeLeft.setText("Auction Finished");
                btnPlaceBid.setDisable(true);
                timeline.stop();
            } else {
                long hours = remaining / 3600000;
                long mins = (remaining % 3600000) / 60000;
                long secs = (remaining % 60000) / 1000;
                lblTimeLeft.setText(String.format("%02d:%02d:%02d", hours, mins, secs));
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }
}
