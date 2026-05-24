package gui.widget;

import client.handler.AuctionEventBus;
import client.handler.ClientAuctionHandler;
import client.service.AuctionService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import model.auction.Auction;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Map;

/**
 * Controller mediating interactive structural bidding layout parameters.
 * Handles dual-mode operations for single manual placements and automated proxy triggers.
 */
public class BidPanelController {

    private static final Logger log = LoggerFactory.getLogger(BidPanelController.class);

    @FXML private TabPane tabPane;
    @FXML private TextField txtManualBid;
    @FXML private Label lblManualError;
    @FXML private Button btnPlaceBid;

    @FXML private TextField txtAutoMax;
    @FXML private TextField txtAutoIncrement;
    @FXML private Label lblAutoError;
    @FXML private Button btnSaveAuto;
    @FXML private Button btnCancelAuto;
    @FXML private Circle dotBotStatus;

    private Auction auction;
    private User currentUser;
    private int retryCount = 0;
    private final PauseTransition retryDelay = new PauseTransition(Duration.millis(300));

    private final PropertyChangeListener auctionUpdateListener = evt -> {
        if (evt.getNewValue() instanceof Auction updated) {
            if (auction != null && auction.getId().equals(updated.getId())) {
                Platform.runLater(() -> syncAuctionState(updated));
            }
        }
    };

    private final PropertyChangeListener errorListener = evt -> {
        if (evt.getNewValue() instanceof Map<?, ?> errorMap) {
            String code = extractErrorCode(errorMap);
            if (code != null && code.contains("CONFLICT") && retryCount < 3) {
                retryCount++;
                log.info("Bidding race condition intercepted (CONFLICT). Scheduling retry #{}...", retryCount);
                retryDelay.setOnFinished(e -> Platform.runLater(this::executeManualBidSubmission));
                retryDelay.play();
            } else {
                Platform.runLater(() -> handleIncomingError(errorMap));
            }
        }
    };

    @FXML
    public void initialize() {
        setupNumericInput(txtManualBid);
        setupNumericInput(txtAutoMax);
        setupNumericInput(txtAutoIncrement);

        btnPlaceBid.setOnAction(e -> triggerManualBidWorkflow());
        btnSaveAuto.setOnAction(e -> triggerSaveAutoBidWorkflow());
        btnCancelAuto.setOnAction(e -> triggerCancelAutoBidWorkflow());

        AuctionEventBus.addListener("UPDATE_AUCTION_PRICE", auctionUpdateListener);
        AuctionEventBus.addListener("ERROR", errorListener);
    }

    public static Parent load(Auction auction, User user) throws IOException {
        FXMLLoader loader = new FXMLLoader(BidPanelController.class.getResource("/gui/widget/BidPanel.fxml"));
        Parent root = loader.load();
        BidPanelController ctrl = loader.getController();
        ctrl.setContext(auction, user);
        return root;
    }

    public void setContext(Auction auction, User user) {
        this.auction = auction;
        this.currentUser = user;
        syncAuctionState(auction);
    }

    public void destroy() {
        AuctionEventBus.removeListener("UPDATE_AUCTION_PRICE", auctionUpdateListener);
        AuctionEventBus.removeListener("ERROR", errorListener);
        retryDelay.stop();
    }

    private void triggerManualBidWorkflow() {
        lblManualError.setText("");
        retryCount = 0;
        executeManualBidSubmission();
    }

    private void executeManualBidSubmission() {
        long amount = parseAmount(txtManualBid.getText());
        long minRequired = (auction.getWinningBidder() == null) ?
                auction.getItem().getStartingPrice() :
                auction.getCurrentPrice() + auction.getBidIncrement();

        if (amount < minRequired) {
            lblManualError.setText("Mức giá đặt tối thiểu phải là: " + formatAmount(minRequired) + " VNĐ");
            return;
        }

        btnPlaceBid.setDisable(true);
        AuctionService.placeBid(auction.getId(), amount);
    }

    private void triggerSaveAutoBidWorkflow() {
        lblAutoError.setText("");
        long maxBid = parseAmount(txtAutoMax.getText());
        long increment = parseAmount(txtAutoIncrement.getPromptText().contains("Bước giá") ?
                String.valueOf(auction.getBidIncrement()) : txtAutoIncrement.getText());

        if (maxBid <= auction.getCurrentPrice()) {
            lblAutoError.setText("Giá tối đa phải lớn hơn giá hiện tại (" + formatAmount(auction.getCurrentPrice()) + " VNĐ)");
            return;
        }
        if (increment < auction.getBidIncrement()) {
            lblAutoError.setText("Bước tăng tối thiểu phải bằng bước giá phiên (" + formatAmount(auction.getBidIncrement()) + " VNĐ)");
            return;
        }

        btnSaveAuto.setDisable(true);
        client.network.NetworkService.sendMessage("SETUP_AUTOBID", Map.of(
                "auctionId", auction.getId(),
                "maxBid", maxBid,
                "increment", increment
        ));
    }

    private void triggerCancelAutoBidWorkflow() {
        lblAutoError.setText("");
        btnCancelAuto.setDisable(true);
        client.network.NetworkService.sendMessage("SETUP_AUTOBID", Map.of(
                "auctionId", auction.getId(),
                "maxBid", 0L,
                "increment", 0L
        ));
    }

    private void syncAuctionState(Auction updated) {
        this.auction = updated;
        btnPlaceBid.setDisable(false);
        btnSaveAuto.setDisable(false);
        btnCancelAuto.setDisable(false);

        long minRequired = (auction.getWinningBidder() == null) ?
                auction.getItem().getStartingPrice() :
                auction.getCurrentPrice() + auction.getBidIncrement();
        txtManualBid.setPromptText("Tối thiểu " + formatAmount(minRequired) + " VNĐ");
        txtAutoIncrement.setPromptText("Bước giá phiên: " + formatAmount(auction.getBidIncrement()) + " VNĐ");

        boolean hasActiveBot = updated.getActiveAutoBids().stream()
                .anyMatch(b -> currentUser != null && b.getBidder().getId().equals(currentUser.getId()));

        if (hasActiveBot) {
            dotBotStatus.setStyle("-fx-fill: #2ecc71;");
            btnCancelAuto.setVisible(true);
        } else {
            dotBotStatus.setStyle("-fx-fill: #95a5a6;");
            btnCancelAuto.setVisible(false);
        }
    }

    private void handleIncomingError(Map<?, ?> errorMap) {
        btnPlaceBid.setDisable(false);
        btnSaveAuto.setDisable(false);
        btnCancelAuto.setDisable(false);

        String msg = errorMap.containsKey("errorMessage") ? errorMap.get("errorMessage").toString() : "Giao dịch thất bại.";
        if (tabPane.getSelectionModel().getSelectedIndex() == 0) {
            lblManualError.setText(msg);
        } else {
            lblAutoError.setText(msg);
        }
    }

    private void setupNumericInput(TextField field) {
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("[\\d,]*")) {
                field.setText(newVal.replaceAll("[^\\d,]", ""));
            }
        });
    }

    private long parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        try {
            return Long.parseLong(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String formatAmount(long amount) {
        return String.format("%,d", amount);
    }

    private String extractErrorCode(Map<?, ?> errorMap) {
        if (errorMap.containsKey("errorCode")) return errorMap.get("errorCode").toString();
        Object rawPayload = errorMap.get("data");
        if (rawPayload instanceof Map) {
            Object code = ((Map<?, ?>) rawPayload).get("errorCode");
            if (code != null) return code.toString();
        }
        return null;
    }
}