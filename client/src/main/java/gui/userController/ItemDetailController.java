package gui.userController;

import client.handler.AuctionEventBus;
import client.service.AdminService;
import client.service.AuctionService;
import gui.process.*;
import gui.widget.CountdownClock;
import javafx.animation.PauseTransition;
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

import javax.management.relation.Role;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Controller for the Auction Item Detail View.
 *
 * <p>Manages the lifecycle of the detail screen including:
 * real-time price updates via EventBus, the bid history line chart,
 * manual bid and auto-bid submission, seller-only auction management controls,
 * and the countdown clock synchronized with server time.</p>
 *
 * <p><b>Lifecycle:</b> Always call {@link #dispose()} when navigating away
 * to unsubscribe EventBus listeners and stop the countdown timeline.</p>
 */
public class ItemDetailController {

    private static final Logger log = LoggerFactory.getLogger(ItemDetailController.class);

    private static final String DEFAULT_IMAGE_URL =
            "https://res.cloudinary.com/de1isjzur/image/upload/v1777703968/iapj7jtzllkfggb0hvxf.jpg";

    private static final int MAX_CHART_POINTS = 20;

    // ── FXML Components ───────────────────────────────────────────────────────
    @FXML private HBox      hbTime;
    @FXML private ImageView imgLarge;
    @FXML private Label     lblDetailTitle;
    @FXML private Label     lblStartPrice;
    @FXML private Label     lblCurrentPrice;
    @FXML private Label     lblLeader;
    @FXML private TextField txtBidAmount;
    @FXML private Button    btnPlaceBid;
    @FXML private TextArea  txtDescription;
    @FXML private TextField txtMaxBid;
    @FXML private TextField txtBidIncrement;
    @FXML private VBox      vbBidHandle;
    @FXML private VBox      vbAuctionControl;
    @FXML private VBox      vbAdminControl;
    @FXML private TextField txtExtendTime;

    @FXML private LineChart<String, Number> bidHistoryChart;
    @FXML private CategoryAxis              xAxisTime;
    @FXML private NumberAxis               yAxisPrice;

    // ── State ─────────────────────────────────────────────────────────────────
    private final User currentUser;
    private Parent detailView;

    private final CountdownClock            lblTimeLeft   = new CountdownClock();
    private final DateTimeFormatter         timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private XYChart.Series<String, Number>  priceSeries;

    private Runnable             onReturnToMarketplace;
    private PropertyChangeListener priceUpdateListener;

    private String currentAuctionId;
    private long   endTimeMillis;
    private long   currentPriceValue = 0L;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Loads {@code Productdetail.fxml} and wires the controller.
     *
     * @param currentUser The authenticated user viewing this detail screen.
     * @throws RuntimeException if the FXML file cannot be loaded.
     */
    public ItemDetailController(User currentUser) {
        this.currentUser = currentUser;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/Productdetail.fxml"));
        loader.setController(this);
        try {
            detailView = loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Productdetail.fxml", e);
        }
        hbTime.getChildren().add(lblTimeLeft);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** @return The root {@link Parent} node of this controller's FXML layout. */
    public Parent getParent() {
        return detailView;
    }

    /**
     * Registers the callback to invoke when the user navigates back to the marketplace.
     *
     * @param callback The {@link Runnable} to execute on back-navigation.
     */
    public void setOnReturnToMarketplace(Runnable callback) {
        this.onReturnToMarketplace = callback;
    }

    /**
     * Populates the view with data from the provided {@link Auction} domain object
     * and starts the countdown clock and EventBus subscriptions.
     *
     * @param auction The auction to display.
     */
    public void setAuctionData(Auction auction) {
        this.currentAuctionId = auction.getId();
        this.endTimeMillis    = auction.getEndTime()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        this.currentPriceValue = auction.getCurrentPrice();

        configureRoleVisibility(auction);
        populateTextFields(auction);
        loadAuctionImage(auction.getItem().getImageUrl());
        addPointToChart(auction.getItem().getStartingPrice(), LocalDateTime.now());
        lblTimeLeft.start(endTimeMillis);
    }

    /**
     * Populates the bid history chart from a list of server-side transaction records.
     *
     * @param transactionHistory A list of maps each containing {@code bid_amount} and {@code bid_time}.
     */
    public void setTransactionHistoryData(List<Map<String, Object>> transactionHistory) { // FIX: renamed from setTransActionHistoryData
        for (Map<String, Object> map : transactionHistory) {
            long          amount  = ((Number) map.get("bid_amount")).longValue();
            LocalDateTime bidTime = LocalDateTime.parse((String) map.get("bid_time"));
            addPointToChart(amount, bidTime);
        }
    }

    /**
     * Unsubscribes EventBus listeners and stops the countdown clock.
     * Must be called when navigating away from this view.
     */
    public void dispose() {
        if (priceUpdateListener != null) {
            AuctionEventBus.removeListener(AuctionEventBus.PRICE_UPDATED, priceUpdateListener);
            AuctionEventBus.removeAllListeners(AuctionEventBus.FETCH_TRANSACTIONS_SUCCESS);
        }
        lblTimeLeft.stop();
        log.debug("ItemDetailController disposed for auction: {}", currentAuctionId);
    }

    // ── FXML Initialize ───────────────────────────────────────────────────────

    /**
     * Called by FXMLLoader. Sets up the price chart and subscribes to real-time price events.
     */
    @FXML
    public void initialize() {
        log.debug("ItemDetailController initialized."); // FIX: was System.out.println
        setupPriceChart();
        registerPriceUpdateListener();
    }

    // ── FXML Event Handlers ───────────────────────────────────────────────────

    @FXML
    private void handleBackToMarketplace() {
        if (onReturnToMarketplace != null) onReturnToMarketplace.run();
    }

    /**
     * Validates the bid amount and sends a bid placement request.
     * Disables the bid panel for 2 seconds to prevent duplicate submissions.
     */
    @FXML
    private void handlePlaceBid() {
        AnimateEffect.pauseNode(vbBidHandle, 2);
        String rawAmount = txtBidAmount.getText().replace(",", "").trim();
        try {
            long bidAmount = Long.parseLong(rawAmount);
            if (bidAmount <= 0) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, "Validation Error", "Bid amount must be greater than 0.");
                return;
            }
            AuctionService.placeBid(currentAuctionId, bidAmount);
        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Validation Error", "Invalid amount format.");
        }
    }

    /**
     * Validates the auto-bid configuration and sends the setup request.
     */
    @FXML
    private void handleAutoBid() {
        AnimateEffect.pauseNode(vbBidHandle, 2);
        String maxBidStr    = txtMaxBid.getText().replace(",", "").trim();
        String incrementStr = txtBidIncrement.getText().replace(",", "").trim();

        if (maxBidStr.isEmpty() || incrementStr.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, "Missing Information", "Please enter both Max Bid and Bid Increment.");
            return;
        }

        try {
            long maxBid       = Long.parseLong(maxBidStr);
            long bidIncrement = Long.parseLong(incrementStr);
            if (maxBid <= 0 || bidIncrement <= 0) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, "Validation Error", "Amounts must be greater than 0.");
                return;
            }
            AuctionService.setAutoBid(currentAuctionId, maxBid, bidIncrement);
            log.info("Auto-bid configured for auction {}: max={}, increment={}", currentAuctionId, maxBid, bidIncrement);
            AlertHelper.showAlert(Alert.AlertType.INFORMATION, "Auto-Bid Active", "Automatic bidding has been configured.");
        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Format Error", "Please enter valid numeric values only.");
        }
    }

    /**
     * Validates the extension duration and sends the time-extension request to the server.
     *
     * <p><b>FIX:</b> The original implementation swallowed both {@link NumberFormatException}
     * and {@link NullPointerException} with only a log message, providing no user feedback.
     * Now uses a guard clause with a proper alert.</p>
     */
    @FXML
    private void handleUpdateTime() {
        String input = txtExtendTime.getText().trim();
        if (input.isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, "Input Required", "Please enter the number of minutes to extend.");
            return;
        }
        try {
            long minutes = Long.parseLong(input);
            AuctionService.extendAuctionTimeMinutes(currentAuctionId, minutes);
        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Format Error", "Extension duration must be a valid whole number.");
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

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Configures the visibility of buyer vs. seller control panels based on ownership.
     */
    private void configureRoleVisibility(Auction auction) {
        if(currentUser.getRole().equals("Admin")) {
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

    /**
     * Sets all text-based UI labels from the auction domain object.
     */
    private void populateTextFields(Auction auction) {
        String leader = (auction.getWinningBidder() != null)
                ? auction.getWinningBidder().getUserName() : "None";

        lblDetailTitle.setText(auction.getItem().getItemName());
        txtDescription.setText(auction.getItem().getDescription());
        lblStartPrice.setText(String.format("%,d VND", auction.getItem().getStartingPrice()));
        lblCurrentPrice.setText(String.format("%,d VND", auction.getCurrentPrice()));
        lblLeader.setText(leader);
    }

    /**
     * Loads the auction item image asynchronously and displays it in the cropped viewport.
     */
    private void loadAuctionImage(String imageUrl) {
        String resolvedUrl = (imageUrl != null) ? imageUrl : DEFAULT_IMAGE_URL;
        Image image = new Image(resolvedUrl, true);
        image.progressProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() == 1.0 && !image.isError()) {
                Platform.runLater(() -> CropImage.cropImage(imgLarge, image, 480, 480));
            }
        });
    }

    /**
     * Creates the chart series and attaches it to the line chart.
     */
    private void setupPriceChart() {
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Bid Price");
        bidHistoryChart.getData().add(priceSeries);
        bidHistoryChart.setCreateSymbols(true);
    }

    /**
     * Subscribes to real-time price updates for the currently viewed auction.
     */
    private void registerPriceUpdateListener() {
        priceUpdateListener = evt -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) evt.getNewValue();
            String auctionId         = (String) data.get("auctionId");

            if (this.currentAuctionId != null && this.currentAuctionId.equals(auctionId)) {
                long   newPrice     = ((Number) data.get("newPrice")).longValue();
                String winnerName   = (String) data.get("winnerName");
                long   newEndTime   = ((Number) data.get("newEndTime")).longValue();

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
    }

    /**
     * Updates the price label with an animated transition and adds a new chart data point.
     */
    private void updateRealTimePrice(long newPrice, String winnerName) {
        long oldPrice          = this.currentPriceValue;
        this.currentPriceValue = newPrice;

        gui.process.PriceTweener.animatePriceChange(lblCurrentPrice, oldPrice, newPrice);
        lblLeader.setText(winnerName);
        addPointToChart(newPrice, Convert.longToTimestamp(System.currentTimeMillis()));
        gui.process.AnimateEffect.highlightText(lblCurrentPrice);
    }

    /**
     * Appends a new data point to the price series, removing the oldest if the limit is exceeded.
     */
    private void addPointToChart(long price, LocalDateTime time) {
        priceSeries.getData().add(new XYChart.Data<>(time.format(timeFormatter), price));
        if (priceSeries.getData().size() > MAX_CHART_POINTS) {
            priceSeries.getData().removeFirst();
        }
    }

}
