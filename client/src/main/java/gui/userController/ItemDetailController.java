package gui.userController;

import client.handler.AuctionEventBus;
import client.service.AdminService;
import client.service.AuctionService;
import gui.process.*;
import gui.widget.BidPanelController;
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
import java.util.Optional;

/**
 * Presenter interface binding an active item profile aggregate root onto structural UI components.
 * Drives chart vector interpolation updates, handles role-based command filtering,
 * and controls a real-time temporal countdown loop synchronized with system frames.
 */
public class ItemDetailController {

    private static final Logger log = LoggerFactory.getLogger(ItemDetailController.class);
    private static final String DEFAULT_IMAGE_URL =
            "https://res.cloudinary.com/de1isjzur/image/upload/v1777703968/iapj7jtzllkfggb0hvxf.jpg";
    private static final int MAX_CHART_POINTS = 20;

    @FXML private VBox bidPanelContainer;
    @FXML private HBox hbTime;
    @FXML private ImageView imgLarge;
    @FXML private Label lblItemType;
    @FXML private Label lblDetailTitle;
    @FXML private Label lblStartPrice;
    @FXML private Label lblStartTime;
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblLeader;
    @FXML private TextArea txtDescription;
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
    private PropertyChangeListener transactionsLoadedListener;

    private BidPanelController bidPanel;

    private Auction currentAuction;
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

    /**
     * Handles the legacy inline "Đặt Giá" button click.
     *
     * <p><b>Bug fix:</b> the previous implementation only checked {@code bidAmount > 0},
     * allowing bids below the auction minimum to reach the server where they would be
     * rejected at a higher cost (network round-trip, unnecessary lock attempt).
     * This patch adds the same {@code minRequired} guard that {@link BidPanelController} uses.
     *
     * @deprecated The legacy form will be removed in Phase 2 of the migration
     *             (see class-level Javadoc). Prefer {@link BidPanelController}.
     */


    @FXML
    private void handleEditAuction() {
        if (currentAuction != null) {
            EditAuction.edit(currentAuction);
        }
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

    @FXML
    private void handleBackToMarketplace() {
        if (onReturnToMarketplace != null) {
            onReturnToMarketplace.run();
        }
    }

    private void configureRoleVisibility(Auction auction) {
        if (currentUser.getRole().equals("Admin")) {
            bidPanelContainer.setVisible(false);
            bidPanelContainer.setManaged(false);
            vbAuctionControl.setVisible(false);
            vbAuctionControl.setManaged(false);
            return;
        }

        vbAdminControl.setVisible(false);
        vbAdminControl.setManaged(false);

        boolean isSeller = auction.getSeller().getId().equals(currentUser.getId());

        // bidPanelContainer chứa BidPanelController widget — ẩn nếu là seller
        bidPanelContainer.setVisible(!isSeller);
        bidPanelContainer.setManaged(!isSeller);

        vbAuctionControl.setVisible(isSeller);
        vbAuctionControl.setManaged(isSeller);
    }

    private void populateTextFields(Auction auction) {
        String leader = (auction.getWinningBidder() != null)
                ? auction.getWinningBidder().getUserName() : "None";

        lblItemType.setText(auction.getItem().getType());
        lblDetailTitle.setText(auction.getItem().getItemName());
        txtDescription.setText(auction.getItem().getDescription());
        lblStartPrice.setText(String.format("%,d VND", auction.getItem().getStartingPrice()));
        
        if (auction.getStartTime() != null) {
            lblStartTime.setText(auction.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } else {
            lblStartTime.setText("Chưa xác định");
        }

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
    }

    /**
     * Populates the view with auction data, starts the countdown clock,
     * and registers the real-time price-update listener on the event bus.
     *
     * @param auction the auction aggregate root to display
     */
    public void setAuctionData(Auction auction) {
        this.currentAuction = auction;
        this.currentAuctionId = auction.getId();
        this.currentPriceValue = auction.getCurrentPrice();

        // Subscribe to real-time price updates for this auction room
        client.network.NetworkService.sendMessage("JOIN_AUCTION", java.util.Map.of("auctionId", currentAuctionId));

        populateTextFields(auction);
        configureRoleVisibility(auction);
        loadAuctionImage(auction.getItem().getImageUrl());
        setupPriceChart();

        // Start countdown timer if the auction has an end time
        LocalDateTime endTime = auction.getEndTime();
        if (endTime != null) {
            endTimeMillis = endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            lblTimeLeft.start(endTimeMillis);
        }
        boolean isBidder = !currentUser.getRole().equals("Admin")
                && !auction.getSeller().getId().equals(currentUser.getId());

        if (isBidder) {
            try {
                // Gọi hàm load tĩnh từ BidPanelController để tạo giao diện đấu giá mới
                bidPanel = BidPanelController.load(auction, currentUser);

                // Đẩy toàn bộ giao diện của Widget mới vào chiếc hộp trống bidPanelContainer trên UI
                bidPanelContainer.getChildren().setAll(bidPanel.getRoot());
            } catch (IOException e) {
                log.error("[ItemDetail] Failed to load BidPanel widget for auction {}: {}",
                        auction.getId(), e.getMessage(), e);
            }
        }

        // Register a listener that filters updates to this specific auction only
        priceUpdateListener = event -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) event.getNewValue();
            if (data == null) {
                return;
            }
            String updatedId = (String) data.get("auctionId");
            if (!currentAuctionId.equals(updatedId)) {
                return;
            }

            long newPrice = ((Number) data.get("newPrice")).longValue();
            String winnerName = (String) data.get("winnerName");
            Object newEndTimeObj = data.get("newEndTime");

            Platform.runLater(() -> {
                currentPriceValue = newPrice;
                lblCurrentPrice.setText(String.format("%,d VND", newPrice));
                if (winnerName != null) {
                    lblLeader.setText(winnerName);
                }
                if (newEndTimeObj != null) {
                    long newEnd = ((Number) newEndTimeObj).longValue();
                    endTimeMillis = newEnd;
                    lblTimeLeft.start(newEnd);
                }
                // Append a point to the bid history chart (keep at most MAX_CHART_POINTS)
                if (priceSeries != null) {
                    String timestamp = LocalDateTime.now().format(timeFormatter);
                    priceSeries.getData().add(
                            new XYChart.Data<>(timestamp, newPrice));
                    if (priceSeries.getData().size() > MAX_CHART_POINTS) {
                        priceSeries.getData().remove(0);
                    }
                }
            });
        };
        AuctionEventBus.addListener(AuctionEventBus.PRICE_UPDATED, priceUpdateListener);

        // Request historical transaction list for the chart
        client.service.AuctionService.fetchTransactions(currentAuctionId);

        transactionsLoadedListener = event -> {
            if (event.getNewValue() instanceof NetworkMessage msg) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> transactions = (List<Map<String, Object>>) msg.getData();
                if (transactions == null) {
                    return;
                }

                Platform.runLater(() -> {
                    if (priceSeries != null) {
                        priceSeries.getData().clear();

                        for (Map<String, Object> t : transactions) {
                            long amount = ((Number) t.get("bid_amount")).longValue();
                            String bidTimeStr = (String) t.get("bid_time");
                            String timeLabel = "";
                            if (bidTimeStr != null && !bidTimeStr.trim().isEmpty()) {
                                try {
                                    LocalDateTime ldt = LocalDateTime.parse(bidTimeStr);
                                    timeLabel = ldt.format(timeFormatter);
                                } catch (Exception e) {
                                    timeLabel = bidTimeStr;
                                }
                            }
                            priceSeries.getData().add(new XYChart.Data<>(timeLabel, amount));
                        }

                        // Crop to MAX_CHART_POINTS
                        while (priceSeries.getData().size() > MAX_CHART_POINTS) {
                            priceSeries.getData().remove(0);
                        }
                    }
                });
            }
        };
        AuctionEventBus.addListener(AuctionEventBus.FETCH_TRANSACTIONS_SUCCESS, transactionsLoadedListener);

        log.debug("[ItemDetail] Loaded auction {} and registered price listener.", currentAuctionId);
    }

    /**
     * Removes the real-time price-update listener from the event bus and stops the countdown clock.
     * Must be called before this controller is discarded to prevent memory leaks.
     */
    public void dispose() {
        if (currentAuctionId != null) {
            // Unsubscribe from real-time updates for this auction room
            client.network.NetworkService.sendMessage("LEAVE_AUCTION", java.util.Map.of("auctionId", currentAuctionId));
        }
        if (priceUpdateListener != null) {
            AuctionEventBus.removeListener(AuctionEventBus.PRICE_UPDATED, priceUpdateListener);
            priceUpdateListener = null;
            log.debug("[ItemDetail] Removed price listener for auction {}.", currentAuctionId);
        }
        if (transactionsLoadedListener != null) {
            AuctionEventBus.removeListener(AuctionEventBus.FETCH_TRANSACTIONS_SUCCESS, transactionsLoadedListener);
            transactionsLoadedListener = null;
            log.debug("[ItemDetail] Removed transactions listener for auction {}.", currentAuctionId);
        }
        lblTimeLeft.stop();
        if (bidPanel != null) {
            bidPanel.destroy(); // Gọi hàm hủy bên trong Widget mới
            bidPanel = null;
        }
    }

    /**
     * Returns the root JavaFX node of this controller's view, for embedding in a parent layout.
     *
     * @return the root {@link Parent} node
     */
    public Parent getParent() {
        return detailView;
    }

    public void setOnReturnToMarketplace(Runnable callback) {
        this.onReturnToMarketplace = callback;
    }
}