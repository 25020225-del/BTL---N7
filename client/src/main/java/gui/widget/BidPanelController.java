package gui.widget;

import client.handler.AuctionEventBus;
import client.handler.ClientAuctionHandler;
import client.service.AuctionService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
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

/**
 * Controller for {@code BidPanel.fxml} — a two-tab widget that embeds into the
 * Item-Detail view and provides:
 *
 * <ul>
 *   <li><b>Tab 1 — Manual Bid:</b> validates input inline and sends {@code PLACE_BID}.</li>
 *   <li><b>Tab 2 — Auto-Bid:</b> validates maxBudget / increment, sends {@code SETUP_AUTOBID},
 *       and reflects the current bot state via a colour-coded signal dot.</li>
 * </ul>
 *
 * <h2>Retry on CONFLICT</h2>
 * <p>If the server responds with an error code containing {@code "CONFLICT"} (a millisecond-level
 * race with another bidder), the controller automatically retries the manual bid up to
 * {@value #MAX_RETRY_COUNT} times with a {@value #RETRY_DELAY_MS} ms pause in between.
 * The UI remains unresponsive during the retry window so the user cannot submit duplicates.</p>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Construct with {@code new BidPanelController(currentUser)}.</li>
 *   <li>Embed the root node via {@link #getRoot()}.</li>
 *   <li>Call {@link #setAuction(Auction)} whenever the displayed auction changes.</li>
 *   <li>Call {@link #dispose()} when navigating away to release all EventBus listeners.</li>
 * </ol>
 */
public class BidPanelController {

    private static final Logger log = LoggerFactory.getLogger(BidPanelController.class);

    // ── Retry configuration ───────────────────────────────────────────────
    /** Maximum number of automatic retries on a CONFLICT response. */
    private static final int MAX_RETRY_COUNT = 2;
    /** Milliseconds between retries (randomised ±50 ms to reduce thundering herd). */
    private static final int RETRY_DELAY_MS  = 300;

    // ── JavaFX node references ─────────────────────────────────────────────
    @FXML private TabPane   bidPanelRoot;

    // Tab 1
    @FXML private TextField txtBidAmount;
    @FXML private Button    btnPlaceBid;
    @FXML private Label     lblMinBidHint;
    @FXML private Label     lblManualError;

    // Tab 2
    @FXML private TextField txtMaxBudget;
    @FXML private TextField txtIncrement;
    @FXML private Button    btnToggleAutoBid;
    @FXML private Label     lblAutoBidStatus;
    @FXML private Label     lblAutoBidError;
    @FXML private Circle    circleStatus;

    // ── State ─────────────────────────────────────────────────────────────
    private final User  currentUser;
    private Auction     currentAuction;
    private boolean     autoBidActive = false;

    // Retry counter for the current pending manual bid
    private int         retryCount    = 0;

    // EventBus listeners kept as fields so we can remove them in dispose()
    private PropertyChangeListener autobidSetupListener;
    private PropertyChangeListener autobidActiveListener;
    private PropertyChangeListener bidSuccessListener;
    private PropertyChangeListener priceUpdatedListener;

    // ── CSS style classes for the signal dot ─────────────────────────────
    private static final String DOT_INACTIVE = "status-dot-inactive"; // grey
    private static final String DOT_ACTIVE   = "status-dot-active";   // green
    private static final String DOT_PENDING  = "status-dot-pending";  // orange/yellow

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor & FXML loading
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads {@code BidPanel.fxml} and wires this instance as the controller.
     *
     * @param currentUser The authenticated user whose session backs this panel.
     * @throws RuntimeException if the FXML cannot be loaded.
     */
    public BidPanelController(User currentUser) {
        this.currentUser = currentUser;

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/gui/widget/BidPanel.fxml"));
        loader.setController(this);
        try {
            loader.load(); // populates @FXML fields
        } catch (IOException e) {
            throw new RuntimeException("Failed to load BidPanel.fxml", e);
        }
    }

    /** @return The root {@link Parent} node to embed in a parent layout. */
    public Parent getRoot() {
        return bidPanelRoot;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Binds this panel to a specific auction and updates the minimum-bid hint.
     * Safe to call multiple times as the user navigates between auctions.
     *
     * @param auction The auction the panel should operate against.
     */
    public void setAuction(Auction auction) {
        this.currentAuction = auction;
        this.retryCount     = 0;
        this.autoBidActive  = false;

        Platform.runLater(() -> {
            long minBid = auction.getCurrentPrice() + auction.getBidIncrement();
            lblMinBidHint.setText("Giá tối thiểu: " + formatAmount(minBid) + " VNĐ");
            resetAutoBidUI();
        });
    }

    /**
     * Releases all EventBus subscriptions.
     * <b>Must</b> be called when the parent controller navigates away.
     */
    public void dispose() {
        if (autobidSetupListener  != null) AuctionEventBus.removeListener(ClientAuctionHandler.AUTOBID_SETUP_SUCCESS, autobidSetupListener);
        if (autobidActiveListener != null) AuctionEventBus.removeListener(ClientAuctionHandler.AUTOBID_ACTIVE,        autobidActiveListener);
        if (bidSuccessListener    != null) AuctionEventBus.removeListener(AuctionEventBus.BID_SUCCESS,                bidSuccessListener);
        if (priceUpdatedListener  != null) AuctionEventBus.removeListener(AuctionEventBus.PRICE_UPDATED,             priceUpdatedListener);
        log.debug("BidPanelController disposed.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FXML initialisation
    // ─────────────────────────────────────────────────────────────────────────

    /** Called by FXMLLoader after all @FXML fields are injected. */
    @FXML
    public void initialize() {
        setupNumericInput(txtBidAmount);
        setupNumericInput(txtMaxBudget);
        setupNumericInput(txtIncrement);
        registerEventBusListeners();
        log.debug("BidPanelController initialized.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FXML event handlers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles "Đặt Giá" button click.
     * Validates client-side before sending; disabled for {@value #RETRY_DELAY_MS} ms
     * after each attempt to prevent duplicate submissions.
     */
    @FXML
    private void handlePlaceBid() {
        if (currentAuction == null) return;
        clearError(lblManualError);

        long bidAmount = parseAmount(txtBidAmount.getText());
        if (bidAmount <= 0) {
            showError(lblManualError, "Vui lòng nhập số tiền hợp lệ.");
            return;
        }

        long minRequired = currentAuction.getCurrentPrice() + currentAuction.getBidIncrement();
        if (bidAmount < minRequired) {
            showError(lblManualError,
                    "Giá đặt phải lớn hơn hoặc bằng " + formatAmount(minRequired) + " VNĐ.");
            return;
        }

        // Disable button to prevent double-click; re-enable after server ack or timeout
        btnPlaceBid.setDisable(true);
        retryCount = 0;

        sendBidWithRetry(bidAmount);
    }

    /**
     * Handles "Kích hoạt / Hủy" toggle button.
     * If auto-bid is currently active, sends a cancel request (maxBid=0).
     * Otherwise validates inputs and sends a setup request.
     */
    @FXML
    private void handleToggleAutoBid() {
        if (currentAuction == null) return;
        clearError(lblAutoBidError);

        if (autoBidActive) {
            // ── Cancel path ──────────────────────────────────────────────
            btnToggleAutoBid.setDisable(true);
            setDotPending();
            lblAutoBidStatus.setText("Đang hủy…");
            AuctionService.setAutoBid(currentAuction.getId(), 0L, 0L);
        } else {
            // ── Activate path ─────────────────────────────────────────────
            long maxBudget = parseAmount(txtMaxBudget.getText());
            long increment = parseAmount(txtIncrement.getText());

            // Client-side validation
            if (maxBudget <= 0) {
                showError(lblAutoBidError, "Vui lòng nhập ngân sách tối đa hợp lệ.");
                return;
            }
            long minRequired = currentAuction.getCurrentPrice() + currentAuction.getBidIncrement();
            if (maxBudget < minRequired) {
                showError(lblAutoBidError,
                        "Ngân sách tối đa phải lớn hơn giá hiện tại + bước tăng (≥ "
                                + formatAmount(minRequired) + " VNĐ).");
                return;
            }
            if (increment <= 0) {
                showError(lblAutoBidError, "Bước tăng phải lớn hơn 0.");
                return;
            }
            if (increment > maxBudget) {
                showError(lblAutoBidError, "Bước tăng không được lớn hơn ngân sách tối đa.");
                return;
            }

            btnToggleAutoBid.setDisable(true);
            setDotPending();
            lblAutoBidStatus.setText("Đang đăng ký…");
            AuctionService.setAutoBid(currentAuction.getId(), maxBudget, increment);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Retry logic (manual bid)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends a {@code PLACE_BID} request. If the server responds with a CONFLICT error,
     * the method schedules up to {@value #MAX_RETRY_COUNT} automatic retries.
     *
     * <p>The conflict listener subscribes a one-shot listener to the {@code GENERAL_ERROR}
     * bus event. Since the event bus does not filter by auction, the listener checks
     * whether the error code contains {@code "CONFLICT"} before retrying.</p>
     *
     * @param bidAmount The validated amount to bid.
     */
    private void sendBidWithRetry(long bidAmount) {
        log.info("Sending bid {} for auction {} (attempt {}/{})",
                bidAmount, currentAuction.getId(), retryCount + 1, MAX_RETRY_COUNT + 1);

        // Register a one-shot listener that decides whether to retry or surface the error
        PropertyChangeListener[] conflictListenerHolder = new PropertyChangeListener[1];
        conflictListenerHolder[0] = evt -> {
            AuctionEventBus.removeListener("GENERAL_ERROR", conflictListenerHolder[0]);

            Object data = evt.getNewValue();
            String errorCode = extractErrorCode(data);

            if (errorCode != null && errorCode.contains("CONFLICT") && retryCount < MAX_RETRY_COUNT) {
                retryCount++;
                int delayMs = RETRY_DELAY_MS + (int)(Math.random() * 100 - 50); // jitter ±50 ms
                log.info("CONFLICT detected — retrying bid in {} ms (attempt {}/{})",
                        delayMs, retryCount + 1, MAX_RETRY_COUNT + 1);

                PauseTransition pause = new PauseTransition(Duration.millis(delayMs));
                pause.setOnFinished(e -> sendBidWithRetry(bidAmount));
                pause.play();
            } else {
                // Final failure — re-enable button and show error
                Platform.runLater(() -> {
                    btnPlaceBid.setDisable(false);
                    retryCount = 0;
                    if (errorCode != null) {
                        showError(lblManualError, "Đặt giá thất bại (mã: " + errorCode + "). Vui lòng thử lại.");
                    }
                });
            }
        };
        AuctionEventBus.addListener("GENERAL_ERROR", conflictListenerHolder[0]);

        AuctionService.placeBid(currentAuction.getId(), bidAmount);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EventBus subscriptions
    // ─────────────────────────────────────────────────────────────────────────

    private void registerEventBusListeners() {

        // ── AUTOBID_SETUP_SUCCESS: server confirmed our registration/cancel ─
        autobidSetupListener = evt -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) evt.getNewValue();

            // Filter: only act if this event is for the auction we're showing
            if (currentAuction == null) return;
            String eventAuctionId = (String) data.get("auctionId");
            if (!currentAuction.getId().equals(eventAuctionId)) return;

            boolean isActive = Boolean.TRUE.equals(data.get("isActive"));
            Platform.runLater(() -> {
                autoBidActive = isActive;
                btnToggleAutoBid.setDisable(false);

                if (isActive) {
                    long maxBid   = ((Number) data.get("maxBid")).longValue();
                    long incr     = ((Number) data.get("increment")).longValue();
                    setDotActive();
                    lblAutoBidStatus.setText(
                            "Đang hoạt động — Tối đa: " + formatAmount(maxBid) + " VNĐ"
                                    + " | Bước: " + formatAmount(incr) + " VNĐ");
                    btnToggleAutoBid.setText("Hủy AutoBid");
                    // Lock fields while auto-bid is active so the user knows the config is live
                    txtMaxBudget.setDisable(true);
                    txtIncrement.setDisable(true);
                } else {
                    resetAutoBidUI();
                }
                log.info("AutoBid UI updated: active={}, auctionId={}", isActive, eventAuctionId);
            });
        };
        AuctionEventBus.addListener(ClientAuctionHandler.AUTOBID_SETUP_SUCCESS, autobidSetupListener);

        // ── AUTOBID_ACTIVE: a bot is firing RIGHT NOW in this auction ───────
        autobidActiveListener = evt -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) evt.getNewValue();

            if (currentAuction == null) return;
            String eventAuctionId = (String) data.get("auctionId");
            if (!currentAuction.getId().equals(eventAuctionId)) return;

            // Only visually indicate if THIS user's bot is the one firing.
            // (The server broadcasts this to all watchers, but the signal dot
            //  is only meaningful to the bot's owner.)
            if (autoBidActive) {
                Platform.runLater(() -> {
                    setDotActive();
                    // Flash the status label briefly to draw attention
                    lblAutoBidStatus.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    PauseTransition pause = new PauseTransition(Duration.seconds(2));
                    pause.setOnFinished(e -> lblAutoBidStatus.setStyle(""));
                    pause.play();
                });
            }
        };
        AuctionEventBus.addListener(ClientAuctionHandler.AUTOBID_ACTIVE, autobidActiveListener);

        // ── BID_SUCCESS: manual bid accepted → re-enable button ─────────────
        bidSuccessListener = evt -> Platform.runLater(() -> {
            btnPlaceBid.setDisable(false);
            retryCount = 0;
            clearError(lblManualError);
        });
        AuctionEventBus.addListener(AuctionEventBus.BID_SUCCESS, bidSuccessListener);

        // ── PRICE_UPDATED: refresh the min-bid hint label ───────────────────
        priceUpdatedListener = evt -> {
            if (currentAuction == null) return;
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) evt.getNewValue();
            String eventAuctionId = (String) data.get("auctionId");
            if (!currentAuction.getId().equals(eventAuctionId)) return;

            long newPrice = ((Number) data.get("newPrice")).longValue();
            // Update cached value for validation
            currentAuction.setCurrentPrice(newPrice);

            Platform.runLater(() -> {
                long minBid = newPrice + currentAuction.getBidIncrement();
                lblMinBidHint.setText("Giá tối thiểu: " + formatAmount(minBid) + " VNĐ");
            });
        };
        AuctionEventBus.addListener(AuctionEventBus.PRICE_UPDATED, priceUpdatedListener);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Resets the Auto-Bid tab to its default (inactive) state. */
    private void resetAutoBidUI() {
        autoBidActive = false;
        setDotInactive();
        lblAutoBidStatus.setText("Chưa kích hoạt");
        lblAutoBidStatus.setStyle("");
        btnToggleAutoBid.setText("Kích hoạt AutoBid");
        btnToggleAutoBid.setDisable(false);
        txtMaxBudget.setDisable(false);
        txtIncrement.setDisable(false);
        clearError(lblAutoBidError);
    }

    private void setDotActive()   { setDot(DOT_ACTIVE);   }
    private void setDotInactive() { setDot(DOT_INACTIVE); }
    private void setDotPending()  { setDot(DOT_PENDING);  }

    private void setDot(String styleClass) {
        circleStatus.getStyleClass().removeAll(DOT_ACTIVE, DOT_INACTIVE, DOT_PENDING);
        circleStatus.getStyleClass().add(styleClass);
    }

    private void showError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearError(Label errorLabel) {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    /**
     * Restricts a {@link TextField} to digits and commas only.
     * Commas are stripped before parsing so users can type "1,000,000".
     */
    private void setupNumericInput(TextField field) {
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            // Allow digits and commas only; reject everything else silently
            if (!newVal.matches("[\\d,]*")) {
                field.setText(newVal.replaceAll("[^\\d,]", ""));
            }
        });
    }

    /**
     * Parses a potentially comma-formatted amount string (e.g. "1,000,000") to a long.
     *
     * @return The parsed value, or {@code -1} if parsing fails.
     */
    private long parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        try {
            return Long.parseLong(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Formats a VNĐ amount with comma separators (e.g. 1000000 → "1,000,000"). */
    private String formatAmount(long amount) {
        return String.format("%,d", amount);
    }

    /**
     * Extracts an error code string from an error event payload.
     * The payload may be a {@link Map} with a {@code "code"} key, or a plain String.
     */
    private String extractErrorCode(Object data) {
        if (data instanceof Map<?, ?> map) {
            Object code = map.get("code");
            return code != null ? code.toString() : null;
        }
        if (data instanceof String s) return s;
        return null;
    }
}