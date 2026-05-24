package gui.userController;

import client.handler.AuctionEventBus;
import client.service.AdminService;
import client.service.AuctionService;
import gui.process.*;
import gui.widget.CountdownClock;
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
import model.auction.Auction;
import model.user.User;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Presenter interface binding an active item profile aggregate root onto structural UI components.
 * Drives chart vector interpolation updates, handles role-based command filtering,
 * and controls a real-time temporal countdown loop synchronized with system frames.
 */
public class ItemDetailController {

    private static final Logger log = LoggerFactory.getLogger(ItemDetailController.class);
    private static final String DEFAULT_IMAGE_URL = "https://res.cloudinary.com/de1isjzur/image/upload/v1777703968/iapj7jtzllkfggb0hvxf.jpg";
    private static final int MAX_CHART_POINTS = 20;

    @FXML private HBox hbTime;
    @FXML private ImageView imgLarge;
    @FXML private Label lblItemType;
    @FXML private Label lblDetailTitle;
    @FXML private Label lblStartPrice;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblLeader;
    @FXML private TextField txtBidAmount;
    @FXML private Button btnPlaceBid;
    @FXML private TextArea txtDescription;
    @FXML private VBox vbBidHandle;
    @FXML private VBox vbAuctionControl;
    @FXML private VBox vbAdminControl;
    @FXML private LineChart<String, Number> bidHistoryChart;
    @FXML private CategoryAxis xAxisTime;
    @FXML private NumberAxis yAxisPrice;

    private final User currentUser;
    private Parent detailView;

    private final CountdownClock lblTimeLeft = new CountdownClock();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private XYChart.Series<String, Number> priceSeries;

    private Runnable onReturnToMarketplace;
    private PropertyChangeListener priceUpdateListener;

    private String currentAuctionId;
    private long endTimeMillis;
    private long currentPriceValue = 0L;

    /**
     * Compiles the explicit detail container window hierarchy and maps security credentials context.
     *
     * @param currentUser the authenticated user entity evaluating the target view context
     */
    public ItemDetailController(User currentUser) {
        this.currentUser = currentUser;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/Productdetail.fxml"));
        loader.setController(this);
        try {
            detailView = loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Fatal error loading component file tree descriptor hierarchy", e);
        }
        hbTime.getChildren().add(lblTimeLeft);
    }

    public Parent getParent() {
        return detailView;
    }

    public void setOnReturnToMarketplace(Runnable callback) {
        this.onReturnToMarketplace = callback;
    }

    /**
     * Binds domain transaction properties onto active structural scene labels.
     * Initiates temporal counters and establishes contextual subscription protocols.
     */
    public void setAuctionData(Auction auction) {
        this.currentAuctionId = auction.getId();
        this.endTimeMillis = auction.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        this.currentPriceValue = auction.getCurrentPrice();

        configureRoleVisibility(auction);
        populateTextFields(auction);
        loadAuctionImage(auction.getItem().getImageUrl());
        addPointToChart(auction.getItem().getStartingPrice(), LocalDateTime.now());
        lblTimeLeft.start(endTimeMillis);
    }

    /**
     * Formats historic transaction matrix lines back onto the multi-dimensional vector grid chart.
     */
    public void setTransactionHistoryData(List<Map<String, Object>> transactionHistory) {
        for (Map<String, Object> map : transactionHistory) {
            long amount = ((Number) map.get("bid_amount")).longValue();
            LocalDateTime bidTime = LocalDateTime.parse((String) map.get("bid_time"));
            addPointToChart(amount, bidTime);
        }
    }

    /**
     * Explicitly detaches listener nodes from the EventBus pipeline maps and terminates
     * the localized countdown scheduling loops to guarantee garbage collection routines.
     */
    public void dispose() {
        if (priceUpdateListener != null) {
            AuctionEventBus.removeListener(AuctionEventBus.PRICE_UPDATED, priceUpdateListener);
            AuctionEventBus.removeAllListeners(AuctionEventBus.FETCH_TRANSACTIONS_SUCCESS);
        }
        lblTimeLeft.stop();
        log.debug("Unsubscribed telemetry context loops for auction entity: {}", currentAuctionId);
    }

    @FXML
    public void initialize() {
        log.debug("ItemDetailController view configuration loaded into execution space.");
        setupPriceChart();
        registerPriceUpdateListener();
    }

    @FXML
    private void handleBackToMarketplace() {
        if (onReturnToMarketplace != null) onReturnToMarketplace.run();
    }

    @FXML
    private void handlePlaceBid() {
        AnimateEffect.pauseNode(vbBidHandle, 2);
        String rawAmount = txtBidAmount.getText().replace(",", "").trim();
        try {
            long bidAmount = Long.parseLong(rawAmount);
            if (bidAmount <= 0) {
                AlertUtils.showError("Validation Error", "Bid amount must be greater than 0.");
                return;
            }
            AuctionService.placeBid(currentAuctionId, bidAmount);
        } catch (NumberFormatException e) {
            AlertUtils.showError("Validation Error", "Invalid amount format.");
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

    @FXML
    private void handleApproveAuction() {
        AdminService.approveAuction(currentAuctionId);
        handleBackToMarketplace();
    }

    @FXML
    private void handleRejectAuction() {
        AdminService.rejectAuction(currentAuctionId);
        handleBackToMarketplace();
    }

    private void configureRoleVisibility(Auction auction) {
        if (currentUser.getRole().equals("Admin")) {
            vbBidHandle.setVisible(false);
            vbAuctionControl.setVisible(false);
            vbBidHandle.setManaged(false);
            vbAuctionControl.setManaged(false);
            return;
        }
        vbAdminControl.setVisible(false);
        vbAdminControl.setManaged(false);
        boolean isSeller = auction.getSeller().getId().equals(currentUser.getId());
        vbBidHandle.setVisible(!isSeller);
        vbBidHandle.setManaged(!isSeller);
        vbAuctionControl.setVisible(isSeller);
        vbAuctionControl.setManaged(isSeller);
    }

    private void populateTextFields(Auction auction) {
        String leader = (auction.getWinningBidder() != null) ? auction.getWinningBidder().getUserName() : "None";

        lblItemType.setText(auction.getItem().getType());
        lblDetailTitle.setText(auction.getItem().getItemName());
        txtDescription.setText(auction.getItem().getDescription());
        lblStartPrice.setText(String.format("%,d VND", auction.getItem().getStartingPrice()));
        lblCurrentPrice.setText(String.format("%,d VND", auction.getCurrentPrice()));
        lblLeader.setText(leader);
    }

    private void loadAuctionImage(String imageUrl) {
        String resolvedUrl = (imageUrl != null) ? imageUrl : DEFAULT_IMAGE_URL;
        Image image = new Image(resolvedUrl, true);
        image.progressProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() == 1.0 && !image.isError()) {
                Platform.runLater(() -> CropImage.cropImage(imgLarge, image, 480, 480));
            }
        });
    }

    private void setupPriceChart() {
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Bid Price");
        bidHistoryChart.getData().add(priceSeries);
        bidHistoryChart.setCreateSymbols(true);
    }

    private void registerPriceUpdateListener() {
        priceUpdateListener = evt -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) evt.getNewValue();
            String auctionId = (String) data.get("auctionId");

            if (this.currentAuctionId != null && this.currentAuctionId.equals(auctionId)) {
                long newPrice = ((Number) data.get("newPrice")).longValue();
                String winnerName = (String) data.get("winnerName");
                long newEndTime = ((Number) data.get("newEndTime")).longValue();

                endTimeMillis = newEndTime;
                lblTimeLeft.start(endTimeMillis);

                Platform.runLater(() -> updateRealTimePrice(newPrice, winnerName));
            }
        };

        AuctionEventBus.addListener(AuctionEventBus.PRICE_UPDATED, priceUpdateListener);

        AuctionEventBus.addListener(AuctionEventBus.FETCH_TRANSACTIONS_SUCCESS, evt -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> history =
                    (List<Map<String, Object>>) ((NetworkMessage) evt.getNewValue()).getData();
            setTransactionHistoryData(history);
        });

        AuctionEventBus.addListener("AUCTION_STATUS_CHANGED", event -> {
            NetworkMessage msg = (NetworkMessage) event.getNewValue();
            Map<String, Object> data = (Map<String, Object>) msg.getData();
            String id = (String) data.get("auctionId");

            if (!id.equals(currentAuctionId)) return;

            String newStatus = (String) data.get("newStatus");
            Platform.runLater(() -> {
                if ("FINISHED".equals(newStatus) || "PAID".equals(newStatus) || "CANCELED".equals(newStatus)) {
                    vbBidHandle.setVisible(false);
                    vbBidHandle.setManaged(false);
                }
            });
        });
    }

    private void updateRealTimePrice(long newPrice, String winnerName) {
        long oldPrice = this.currentPriceValue;
        this.currentPriceValue = newPrice;

        PriceTweener.animatePriceChange(lblCurrentPrice, oldPrice, newPrice);
        lblLeader.setText(winnerName);
        addPointToChart(newPrice, Convert.longToTimestamp(System.currentTimeMillis()));
        AnimateEffect.highlightText(lblCurrentPrice);
    }

    private void addPointToChart(long price, LocalDateTime time) {
        priceSeries.getData().add(new XYChart.Data<>(time.format(timeFormatter), price));
        if (priceSeries.getData().size() > MAX_CHART_POINTS) {
            priceSeries.getData().removeFirst();
        }
    }
}