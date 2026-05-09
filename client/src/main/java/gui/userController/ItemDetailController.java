package gui.userController;

import client.handler.AuctionEventBus;
import gui.MainApplication;
import gui.process.AlertHelper;
import gui.process.CropImage;
import gui.widget.CountdownClock;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import model.auction.Auction;
import model.user.User;
import utils.TimeUtil;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for the Auction Item Detail View.
 * Handles countdown synchronization, UI population, manual bidding,
 * and rendering the real-time bid history line chart.
 */
public class ItemDetailController {
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

    public Parent getParent(){
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
                String newEndTimeStr = (String) data.get("newEndTime");
                if (newEndTimeStr != null) {
                    long newEndTimeMillis = LocalDateTime.parse(newEndTimeStr)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli();

                    endTimeMillis = newEndTimeMillis;
                    lblTimeLeft.start(newEndTimeMillis);
                }
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

        // Configure Bidding Button Action
        btnPlaceBid.setOnAction(e -> handlePlaceBid());

        startCountdown();
    }

    public void setTransActionHistoryData(List<Map<String, Object>> transActionHistoryData) {
        for(Map<String, Object> map : transActionHistoryData){
            String bidAmount = (String) map.get("bid_amount");
            String bidTime = (String) map.get("bid_time");
            LocalDateTime time = LocalDateTime.parse(bidTime);
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
        addPointToChart(newPrice, LocalDateTime.now());

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


    @FXML
    private void handleEditAuction() {
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Edit Auction");
        dialog.setHeaderText("Update details for auction: " + currentAuctionId);

        ButtonType saveButtonType = new ButtonType("Save Changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        TextArea descField = new TextArea(); descField.setPrefRowCount(3);
        TextField priceField = new TextField();

        grid.add(new Label("Item Name:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Description:"), 0, 1); grid.add(descField, 1, 1);
        grid.add(new Label("Starting Price:"), 0, 2); grid.add(priceField, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                Map<String, String> result = new HashMap<>();
                result.put("auctionId", currentAuctionId);
                result.put("itemName", nameField.getText());
                result.put("description", descField.getText());
                result.put("startPrice", priceField.getText());
                return result;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(data -> {
            if (data.get("itemName").isEmpty() || data.get("startPrice").isEmpty()) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, "Validation Error", "Name and Price cannot be empty.");
                return;
            }
            try {
                Double.parseDouble(data.get("startPrice"));
                MainApplication.networkClient.sendMessage("EDIT_AUCTION", data);
            } catch (NumberFormatException e) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, "Validation Error", "Invalid price format.");
            }
        });
    }

    @FXML
    private void handleDeleteAuction() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText("Delete Auction?");
        confirm.setContentText("Are you sure you want to delete auction: " + currentAuctionId + "?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                MainApplication.networkClient.sendMessage("DELETE_AUCTION", currentAuctionId);
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
