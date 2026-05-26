// ============================================================
// FILE: client/src/main/java/gui/widget/BidPanelController.java
// CHANGES:
//   • Added PendingOperation enum — tracks last-dispatched operation so
//     async error responses always reach the correct label, regardless of
//     which tab is currently selected.
//   • Extracted computeMinRequired() — single source of truth used by the
//     manual-bid validator, the prompt-text hint, AND the retry path, so
//     all three stay in sync.
//   • handleIncomingError() now routes to lblManualError or lblAutoError
//     based on pendingOperation rather than the active tab.
//   • FXML fx:id fields aligned (see BidPanel.fxml patch below).
// ============================================================

package gui.widget;

import client.handler.AuctionEventBus;
import client.network.NetworkService;
import client.service.AuctionService;
import gui.process.AlertUtils;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import model.auction.Auction;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for the unified bidding widget ({@code BidPanel.fxml}).
 *
 * <p>Hosts two tabs — manual bid and auto-bid — and is the single source of
 * client-side bid logic. The legacy inline form in {@code ItemDetailController}
 * is deprecated in favour of this widget (see migration note below).
 *
 * <h3>Error routing</h3>
 * Server error responses arrive asynchronously on a background thread and are
 * dispatched through {@link AuctionEventBus}. Because the user may switch tabs
 * between the time a request is sent and the response arrives, the active tab
 * cannot be used to determine which label to update. Instead, {@link #pendingOperation}
 * is set synchronously in the dispatch methods and read inside
 * {@link #handleIncomingError} to guarantee the error always lands on the
 * correct label.
 *
 * <h3>Migration from ItemDetailController legacy form</h3>
 * <ol>
 *   <li>Remove {@code vbBidHandle} (TextField + Button) from {@code Productdetail.fxml}.</li>
 *   <li>In its place, add an {@code fx:id="bidPanelContainer"} placeholder node (e.g. {@code VBox}).</li>
 *   <li>In {@code ItemDetailController.setAuctionData()}, call
 *       {@code BidPanelController.load(auction, currentUser)} and attach the returned
 *       root node to the container.</li>
 *   <li>Forward the {@code destroy()} call from {@code ItemDetailController.dispose()}.</li>
 * </ol>
 */
public class BidPanelController {

    private static final Logger log = LoggerFactory.getLogger(BidPanelController.class);

    // ── FXML bindings ────────────────────────────────────────────────────────

    @FXML private TabPane tabPane;

    // Manual-bid tab
    @FXML private TextField txtManualBid;
    @FXML private Label     lblManualError;
    @FXML private Button    btnPlaceBid;

    // Auto-bid tab
    @FXML private TextField txtAutoMax;
    @FXML private TextField txtAutoIncrement;
    @FXML private Label     lblAutoError;
    @FXML private Label     lblAutoBidStatus;
    @FXML private Button    btnSaveAuto;
    @FXML private Button    btnCancelAuto;
    @FXML private Circle    dotBotStatus;

    // ── State ────────────────────────────────────────────────────────────────

    /** Tracks which operation is currently in-flight so errors route to the correct label. */
    private volatile PendingOperation pendingOperation = PendingOperation.NONE;

    private Auction auction;
    private User currentUser;

    private int retryCount = 0;
    private final PauseTransition retryDelay = new PauseTransition(Duration.millis(300));

    // ── Inner types ──────────────────────────────────────────────────────────

    /**
     * Discriminates the last bid operation dispatched to the server so that
     * asynchronous error responses can be routed to the correct UI label even
     * when the user has switched tabs since the request was sent.
     */
    public enum PendingOperation {
        /** No operation currently in flight. */
        NONE,
        /** A manual (single) bid request was dispatched. */
        MANUAL,
        /** An auto-bid setup or cancellation request was dispatched. */
        AUTOBID
    }

    // ── Listeners ────────────────────────────────────────────────────────────

    private final PropertyChangeListener auctionUpdateListener = evt -> {
        if (evt.getNewValue() instanceof Map<?, ?> data) {
            String updatedId = (String) data.get("auctionId");
            if (auction != null && auction.getId().equals(updatedId)) {
                long newPrice = ((Number) data.get("newPrice")).longValue();
                String winnerName = (String) data.get("winnerName");

                auction.setCurrentPrice(newPrice);
                if (winnerName != null) {
                    User winner = new User();
                    winner.setUserName(winnerName);
                    auction.setWinningBidder(winner);
                } else {
                    auction.setWinningBidder(null);
                }

                Platform.runLater(() -> syncAuctionState(auction));
            }
        }
    };

    private final PropertyChangeListener errorListener = evt -> {
        Object rawVal = evt.getNewValue();
        if (rawVal instanceof Map<?, ?> errorMap) {
            String code = extractErrorCode(errorMap);
            if (code != null && code.contains("CONFLICT") && retryCount < 3) {
                retryCount++;
                log.info("Bid race condition intercepted (CONFLICT). Scheduling retry #{}…", retryCount);
                retryDelay.setOnFinished(e -> Platform.runLater(this::executeManualBidSubmission));
                retryDelay.play();
            } else {
                Platform.runLater(() -> handleIncomingError(errorMap));
            }
        } else if (rawVal instanceof String errorMsg) {
            Platform.runLater(() -> {
                btnPlaceBid.setDisable(false);
                btnSaveAuto.setDisable(false);
                btnCancelAuto.setDisable(false);
                switch (pendingOperation) {
                    case AUTOBID -> showAutoError(errorMsg);
                    case MANUAL, NONE -> showManualError(errorMsg);
                }
                pendingOperation = PendingOperation.NONE;
            });
        } else {
            Platform.runLater(() -> {
                btnPlaceBid.setDisable(false);
                btnSaveAuto.setDisable(false);
                btnCancelAuto.setDisable(false);
                pendingOperation = PendingOperation.NONE;
            });
        }
    };

    private final PropertyChangeListener bidSuccessListener = evt -> {
        Platform.runLater(() -> {
            btnPlaceBid.setDisable(false);
            txtManualBid.clear();
            String msg = evt.getNewValue() != null ? evt.getNewValue().toString() : "Đặt giá thành công!";
            AlertUtils.showInfo("Thành công", msg);
        });
    };

    private final PropertyChangeListener autoBidSuccessListener = evt -> {
        if (evt.getNewValue() instanceof Map<?, ?> data) {
            String updatedId = (String) data.get("auctionId");
            if (auction != null && auction.getId().equals(updatedId)) {
                Platform.runLater(() -> {
                    btnSaveAuto.setDisable(false);
                    btnCancelAuto.setDisable(false);
                    boolean isActive = Boolean.TRUE.equals(data.get("isActive"));
                    if (isActive) {
                        dotBotStatus.setStyle("-fx-fill: #2ecc71;");
                        lblAutoBidStatus.setText("Đã kích hoạt");
                        btnCancelAuto.setVisible(true);
                        btnCancelAuto.setManaged(true);
                        btnSaveAuto.setVisible(false);
                        btnSaveAuto.setManaged(false);
                    } else {
                        dotBotStatus.setStyle("-fx-fill: #95a5a6;");
                        lblAutoBidStatus.setText("Chưa kích hoạt");
                        btnCancelAuto.setVisible(false);
                        btnCancelAuto.setManaged(false);
                        btnSaveAuto.setVisible(true);
                        btnSaveAuto.setManaged(true);
                    }
                });
            }
        }
    };

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /** Wires button handlers and registers event-bus listeners. */
    @FXML
    public void initialize() {
        setupNumericInput(txtManualBid);
        setupNumericInput(txtAutoMax);
        setupNumericInput(txtAutoIncrement);

        btnPlaceBid.setOnAction(e -> triggerManualBidWorkflow());
        btnSaveAuto.setOnAction(e -> triggerSaveAutoBidWorkflow());
        btnCancelAuto.setOnAction(e -> triggerCancelAutoBidWorkflow());

        AuctionEventBus.addListener(AuctionEventBus.PRICE_UPDATED, auctionUpdateListener);
        AuctionEventBus.addListener("ERROR", errorListener);
        AuctionEventBus.addListener(AuctionEventBus.BID_SUCCESS, bidSuccessListener);
        AuctionEventBus.addListener("AUTOBID_SETUP_SUCCESS", autoBidSuccessListener);
    }

    /**
     * Loads the widget from FXML and binds it to the given auction and user.
     *
     * @param auction     the auction to display and interact with
     * @param currentUser the authenticated user
     * @return the fully initialised controller
     * @throws IOException if the FXML resource cannot be loaded
     */
    public static BidPanelController load(Auction auction, User currentUser) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                BidPanelController.class.getResource("/gui/BidPanel.fxml"));
        loader.load();
        BidPanelController ctrl = loader.getController();
        ctrl.auction = auction;
        ctrl.currentUser = currentUser;
        ctrl.syncAuctionState(auction);
        return ctrl;
    }

    /**
     * Removes event-bus listeners and stops animations.
     * Must be called before this controller is discarded to prevent memory leaks.
     */
    public void destroy() {
        AuctionEventBus.removeListener(AuctionEventBus.PRICE_UPDATED, auctionUpdateListener);
        AuctionEventBus.removeListener("ERROR", errorListener);
        AuctionEventBus.removeListener(AuctionEventBus.BID_SUCCESS, bidSuccessListener);
        AuctionEventBus.removeListener("AUTOBID_SETUP_SUCCESS", autoBidSuccessListener);
        retryDelay.stop();
        pendingOperation = PendingOperation.NONE;
    }

    // ── Manual-bid workflow ──────────────────────────────────────────────────

    /**
     * Validates the manual bid amount against the current minimum, shows a
     * confirmation dialog, then dispatches to the server.
     *
     * <p>The validation uses {@link #computeMinRequired()} — the same helper
     * used by {@link #syncAuctionState} and {@link #executeManualBidSubmission} —
     * ensuring all three paths apply identical rules.
     */
    private void triggerManualBidWorkflow() {
        clearErrors();
        retryCount = 0;

        if (auction.getStartTime() != null && java.time.LocalDateTime.now().isBefore(auction.getStartTime())) {
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            AlertUtils.showError("Phiên đấu giá chưa bắt đầu", "Phiên đấu giá chưa bắt đầu! Vui lòng đợi đến: " + auction.getStartTime().format(dtf));
            return;
        }

        long amount = parseAmount(txtManualBid.getText());
        long minRequired = computeMinRequired();

        if (amount < minRequired) {
            showManualError("Mức giá đặt tối thiểu phải là: " + formatAmount(minRequired) + " VNĐ");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận đặt giá");
        confirm.setHeaderText("Bạn có chắc chắn muốn đặt giá?");
        confirm.setContentText(String.format(
                "Số tiền:  %s VNĐ%nPhiên:    %s%n%n"
                        + "Hành động này sẽ khóa tiền trong ví của bạn và không thể hoàn tác ngay lập tức.",
                formatAmount(amount), auction.getId()));

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            executeManualBidSubmission();
        }
    }

    /**
     * Re-validates and dispatches the manual bid. Called directly on first attempt and
     * by the retry scheduler on CONFLICT responses.
     */
    private void executeManualBidSubmission() {
        long amount = parseAmount(txtManualBid.getText());
        long minRequired = computeMinRequired();

        if (amount < minRequired) {
            showManualError("Mức giá đặt tối thiểu phải là: " + formatAmount(minRequired) + " VNĐ");
            return;
        }

        pendingOperation = PendingOperation.MANUAL;
        btnPlaceBid.setDisable(true);
        AuctionService.placeBid(auction.getId(), amount);
    }

    // ── Auto-bid workflow ────────────────────────────────────────────────────

    /**
     * Validates auto-bid inputs and dispatches a {@code SETUP_AUTOBID} request.
     */
    private void triggerSaveAutoBidWorkflow() {
        clearErrors();

        if (auction.getStartTime() != null && java.time.LocalDateTime.now().isBefore(auction.getStartTime())) {
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            AlertUtils.showError("Phiên đấu giá chưa bắt đầu", "Phiên đấu giá chưa bắt đầu! Vui lòng đợi đến: " + auction.getStartTime().format(dtf));
            return;
        }

        long maxBid = parseAmount(txtAutoMax.getText());
        String incrementText = txtAutoIncrement.getText();
        long increment = incrementText.isBlank()
                ? auction.getBidIncrement()
                : parseAmount(incrementText);

        if (maxBid <= auction.getCurrentPrice()) {
            showAutoError("Giá tối đa phải lớn hơn giá hiện tại ("
                    + formatAmount(auction.getCurrentPrice()) + " VNĐ)");
            return;
        }
        if (increment < auction.getBidIncrement()) {
            showAutoError("Bước tăng tối thiểu phải bằng bước giá phiên ("
                    + formatAmount(auction.getBidIncrement()) + " VNĐ)");
            return;
        }

        pendingOperation = PendingOperation.AUTOBID;
        btnSaveAuto.setDisable(true);
        NetworkService.sendMessage("SETUP_AUTOBID", Map.of(
                "auctionId", auction.getId(),
                "maxBid",    maxBid,
                "increment", increment));
    }

    /**
     * Dispatches an auto-bid cancellation request.
     */
    private void triggerCancelAutoBidWorkflow() {
        clearErrors();
        pendingOperation = PendingOperation.AUTOBID;
        btnCancelAuto.setDisable(true);
        NetworkService.sendMessage("SETUP_AUTOBID", Map.of(
                "auctionId", auction.getId(),
                "maxBid",    0L,
                "increment", 0L));
    }

    // ── State sync ───────────────────────────────────────────────────────────

    /**
     * Refreshes all UI elements to reflect the latest auction state.
     * Called on the JavaFX Application Thread via {@link Platform#runLater}.
     *
     * @param updated the freshly received auction snapshot
     */
    private void syncAuctionState(Auction updated) {
        this.auction = updated;
        pendingOperation = PendingOperation.NONE;

        btnPlaceBid.setDisable(false);
        btnSaveAuto.setDisable(false);
        btnCancelAuto.setDisable(false);

        long minRequired = computeMinRequired();
        txtManualBid.setPromptText("Tối thiểu " + formatAmount(minRequired) + " VNĐ");
        txtAutoIncrement.setPromptText("Bước giá phiên: " + formatAmount(auction.getBidIncrement()) + " VNĐ");

        boolean hasActiveBot = updated.getActiveAutoBids().stream()
                .anyMatch(b -> currentUser != null
                        && b.getBidder().getId().equals(currentUser.getId()));

        if (hasActiveBot) {
            dotBotStatus.setStyle("-fx-fill: #2ecc71;");
            lblAutoBidStatus.setText("Đã kích hoạt");
            btnCancelAuto.setVisible(true);
            btnCancelAuto.setManaged(true);
            btnSaveAuto.setVisible(false);
            btnSaveAuto.setManaged(false);
        } else {
            dotBotStatus.setStyle("-fx-fill: #95a5a6;");
            lblAutoBidStatus.setText("Chưa kích hoạt");
            btnCancelAuto.setVisible(false);
            btnCancelAuto.setManaged(false);
            btnSaveAuto.setVisible(true);
            btnSaveAuto.setManaged(true);
        }
    }

    // ── Error handling ───────────────────────────────────────────────────────

    /**
     * Routes an incoming server error to the label that corresponds to the
     * in-flight operation, regardless of which tab is currently active.
     *
     * @param errorMap the raw error payload received from the server
     */
    private void handleIncomingError(Map<?, ?> errorMap) {
        btnPlaceBid.setDisable(false);
        btnSaveAuto.setDisable(false);
        btnCancelAuto.setDisable(false);

        String msg = extractErrorMessage(errorMap);
        if (msg == null) return;

        switch (pendingOperation) {
            case AUTOBID -> showAutoError(msg);
            case MANUAL, NONE -> showManualError(msg);
        }

        pendingOperation = PendingOperation.NONE;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Computes the minimum valid bid amount for the current auction state.
     * This is the single source of truth used by validation, prompt-text, and retry paths.
     *
     * @return minimum required bid in VNĐ
     */
    private long computeMinRequired() {
        return (auction.getWinningBidder() == null)
                ? auction.getItem().getStartingPrice()
                : auction.getCurrentPrice() + auction.getBidIncrement();
    }

    private void showManualError(String message) {
        lblManualError.setText(message);
        lblManualError.setVisible(true);
        lblManualError.setManaged(true);
    }

    private void showAutoError(String message) {
        lblAutoError.setText(message);
        lblAutoError.setVisible(true);
        lblAutoError.setManaged(true);
    }

    private void clearErrors() {
        lblManualError.setText("");
        lblManualError.setVisible(false);
        lblManualError.setManaged(false);
        lblAutoError.setText("");
        lblAutoError.setVisible(false);
        lblAutoError.setManaged(false);
    }

    private String extractErrorCode(Map<?, ?> map) {
        Object code = map.get("errorCode");
        return code != null ? code.toString() : null;
    }

    private String extractErrorMessage(Map<?, ?> map) {
        Object msg = map.get("errorMessage");
        return msg != null ? msg.toString() : null;
    }

    private long parseAmount(String text) {
        if (text == null || text.isBlank()) return 0L;
        try {
            return Long.parseLong(text.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String formatAmount(long amount) {
        return String.format("%,d", amount);
    }

    private void setupNumericInput(TextField field) {
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.matches("[0-9,]*")) {
                field.setText(oldVal);
            }
        });
    }

    /** Returns the loaded JavaFX root node for embedding in a parent layout. */
    public javafx.scene.Parent getRoot() {
        return tabPane;
    }
}