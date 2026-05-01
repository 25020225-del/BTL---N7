package gui;

import gui.process.AlertHelper;
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

    @FXML private ImageView imgLarge;
    @FXML private Label lblDetailTitle;
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblLeader;
    @FXML private Label lblTimeLeft;
    @FXML private TextField txtBidAmount;
    @FXML private Button btnPlaceBid;
    @FXML private TextArea txtDescription;

    // --- Chart Components ---
    @FXML private LineChart<String, Number> bidHistoryChart;
    @FXML private CategoryAxis xAxisTime;
    @FXML private NumberAxis yAxisPrice;
    private XYChart.Series<String, Number> priceSeries;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Timeline timeline;
    private String currentAuctionId;
    private long endTimeMillis;

    @FXML
    public void initialize() {
        System.out.println("[System]: Item Detail View Initialized.");
        setupChart();
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
     * Populates the UI with detailed auction data received from the server.
     *
     * @param auctionData A map containing the auction details.
     */
    public void setProductData(Map<String, Object> auctionData) {
        this.currentAuctionId = (String) auctionData.get("id");

        // Safely extract values
        String name = (String) auctionData.get("itemName");
        String desc = (String) auctionData.get("description");
        double startPrice = ((Number) auctionData.get("startingPrice")).doubleValue();
        double currentPrice = ((Number) auctionData.get("currentPrice")).doubleValue();
        this.endTimeMillis = ((Number) auctionData.get("endTime")).longValue();

        String leader = auctionData.containsKey("winnerName") && auctionData.get("winnerName") != null
                ? (String) auctionData.get("winnerName") : "None";

        // Handle Base64 Image if available
        if (auctionData.containsKey("imageData")) {
            String base64Img = (String) auctionData.get("imageData");
            Image img = ImageUtil.decodeBase64ToImage(base64Img);
            if (img != null) {
                imgLarge.setImage(img);
            }
        }

        // Set UI text
        lblDetailTitle.setText(name);
        txtDescription.setText(desc);
        lblStartPrice.setText(String.format("%,.0f VND", startPrice));
        lblCurrentPrice.setText(String.format("%,.0f VND", currentPrice));
        lblLeader.setText(leader);

        // Add the initial starting point to the chart
        addPointToChart(startPrice);

        // Configure Bidding Button Action
        btnPlaceBid.setOnAction(e -> handlePlaceBid());

        startCountdown();
    }

    /**
     * Triggered by the global ResponseDispatcher when a new bid is placed.
     * Updates the local UI dynamically.
     *
     * @param newPrice   The newly established price.
     * @param winnerName The username of the user who placed the bid.
     */
    public void updateRealTimePrice(double newPrice, String winnerName) {
        // Ensure UI updates run on the JavaFX Application Thread
        Platform.runLater(() -> {
            lblCurrentPrice.setText(String.format("%,.0f VND", newPrice));
            lblLeader.setText(winnerName);

            // Add a new dynamic node to the line chart
            addPointToChart(newPrice);

            // Add an engaging visual highlight effect to the price label
            gui.process.AnimateEffect.highlightText(lblCurrentPrice);
        });
    }

    /**
     * Adds a new data point to the active LineChart.
     */
    private void addPointToChart(double price) {
        String currentTimeStr = LocalDateTime.now().format(timeFormatter);
        XYChart.Data<String, Number> newPoint = new XYChart.Data<>(currentTimeStr, price);
        priceSeries.getData().add(newPoint);

        // Optional: Keep chart clean by removing old nodes if there are too many (e.g., > 20)
        if (priceSeries.getData().size() > 20) {
            priceSeries.getData().remove(0);
        }
    }

    /**
     * Handles the manual bid placement logic.
     */
    private void handlePlaceBid() {
        try {
            double bidAmount = Double.parseDouble(txtBidAmount.getText().replace(",", ""));

            if (bidAmount <= 0) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, "Error", "Amount must be greater than 0");
                return;
            }

            // Create payload map for the server
            Map<String, Object> payload = Map.of(
                    "auctionId", currentAuctionId,
                    "bidAmount", bidAmount,
                    "isAutoBid", false
            );

            System.out.println("[Log]: Submitting manual bid of " + bidAmount + " for " + currentAuctionId);
            MainApplication.networkClient.sendMessage("PLACE_BID", payload);

            txtBidAmount.clear();

        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Format Error", "Please enter a valid numeric amount");
        }
    }

    /**
     * Manages the synchronized countdown timer for the auction end time.
     */
    private void startCountdown() {
        if (timeline != null) timeline.stop();

        timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            // Utilize the NTP-style synchronized time utility
            long diffMillis = endTimeMillis - utils.TimeUtil.getCurrentServerTime();

            if (diffMillis <= 0) {
                timeline.stop();
                lblTimeLeft.setText("AUCTION HAS ENDED!");
                lblTimeLeft.setStyle("-fx-text-fill: gray;");
                btnPlaceBid.setDisable(true);
                txtBidAmount.setDisable(true);
            } else {
                long seconds = diffMillis / 1000;
                long h = seconds / 3600;
                long m = (seconds % 3600) / 60;
                long s = seconds % 60;
                lblTimeLeft.setText(String.format("Time left: %02d:%02d:%02d", h, m, s));
            }
        }));

        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }
}