package gui.process;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Non-modal slide-in toast notification displayed when the current user's
 * leading bid is overtaken by another bidder.
 *
 * <p>The toast appears in the bottom-right corner of the primary stage,
 * slides in from the right with a fade, lingers for {@value #DISPLAY_SECONDS}
 * seconds, then fades out automatically.  The user may also dismiss it early
 * by clicking the × button.
 *
 * <p>All methods are safe to call from any thread; UI work is always
 * marshalled onto the JavaFX Application Thread via {@link Platform#runLater}.
 *
 * <p>Usage:
 * <pre>{@code
 *   OutbidToast.show("AUC-001", 5_000_000L);
 * }</pre>
 */
public final class OutbidToast {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final int    DISPLAY_SECONDS   = 6;
    private static final int    SLIDE_MILLIS      = 380;
    private static final int    FADE_OUT_MILLIS   = 400;
    private static final double TOAST_WIDTH       = 340.0;
    private static final double MARGIN            = 20.0;
    private static final double STAGE_BOTTOM_GAP  = 54.0;

    // ── Color tokens ──────────────────────────────────────────────────────────

    private static final String CLR_BG            = "#16213e";
    private static final String CLR_ACCENT        = "#e74c3c";
    private static final String CLR_ACCENT_HOVER  = "#c0392b";
    private static final String CLR_TEXT_PRIMARY   = "#ecf0f1";
    private static final String CLR_TEXT_SECONDARY = "#95a5a6";
    private static final String CLR_CLOSE_HOVER   = "rgba(231,76,60,0.15)";

    // ── Public API ────────────────────────────────────────────────────────────

    private OutbidToast() {
        throw new UnsupportedOperationException("OutbidToast is a static utility class.");
    }

    /**
     * Shows an outbid toast for the given auction on the application's primary stage.
     * Safe to call from any thread.
     *
     * @param auctionId the ID of the auction in which the user was outbid
     * @param newPrice  the new leading price that overtook the user's bid (in VNĐ)
     */
    public static void show(String auctionId, long newPrice) {
        if (Platform.isFxApplicationThread()) {
            renderToast(auctionId, newPrice);
        } else {
            Platform.runLater(() -> renderToast(auctionId, newPrice));
        }
    }

    private static void renderToast(String auctionId, long newPrice) {
        Stage owner = resolveOwnerStage();
        if (owner == null || !owner.isShowing()) return;

        // ── Root card ────────────────────────────────────────────────────────
        VBox card = buildCard(auctionId, newPrice);

        // ── Left accent bar (4 px) ───────────────────────────────────────────
        Rectangle accentBar = new Rectangle(4, 80);
        accentBar.setStyle("-fx-fill: " + CLR_ACCENT + "; -fx-arc-width: 4; -fx-arc-height: 4;");

        HBox root = new HBox(accentBar, card);
        root.setMaxWidth(TOAST_WIDTH);
        root.setStyle(
                "-fx-background-color: " + CLR_BG + ";"
                        + "-fx-background-radius: 10;"
                        + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 22, 0.25, 0, 6);"
        );

        // ── Popup ─────────────────────────────────────────────────────────────
        Popup popup = new Popup();
        popup.setAutoFix(false);
        popup.setAutoHide(false);
        popup.getContent().add(root);

        // Position after the popup is shown so we know its width/height
        popup.setOnShown(e -> positionPopup(popup, root, owner));
        popup.show(owner);

        // ── Entrance animation ────────────────────────────────────────────────
        root.setOpacity(0);
        root.setTranslateX(TOAST_WIDTH + 40);

        TranslateTransition slide = new TranslateTransition(Duration.millis(SLIDE_MILLIS), root);
        slide.setToX(0);
        slide.setInterpolator(Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0)); // spring-like ease-out

        FadeTransition fadeIn = new FadeTransition(Duration.millis(SLIDE_MILLIS), root);
        fadeIn.setToValue(1.0);

        // ── Auto-dismiss sequence ─────────────────────────────────────────────
        SequentialTransition lifecycle = buildDismissSequence(root, popup);
        new ParallelTransition(slide, fadeIn).play();
        lifecycle.play();

        // ── Close button callback ─────────────────────────────────────────────
        Button closeBtn = (Button) card.lookup("#outbidCloseBtn");
        if (closeBtn != null) {
            closeBtn.setOnAction(evt -> {
                lifecycle.stop();
                dismissImmediately(root, popup);
            });
        }
    }

    // ── Card builder ──────────────────────────────────────────────────────────

    private static VBox buildCard(String auctionId, long newPrice) {
        // Header row: bell icon + title + close button
        Label bellIcon = new Label("🔔");
        bellIcon.setStyle("-fx-font-size: 18px;");

        Label titleLbl = new Label("Bid của bạn đã bị vượt!");
        titleLbl.setStyle(
                "-fx-text-fill: " + CLR_ACCENT + ";"
                        + "-fx-font-size: 13.5px;"
                        + "-fx-font-weight: bold;"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("×");
        closeBtn.setId("outbidCloseBtn");
        closeBtn.setStyle(
                "-fx-background-color: transparent;"
                        + "-fx-text-fill: " + CLR_TEXT_SECONDARY + ";"
                        + "-fx-font-size: 18px;"
                        + "-fx-cursor: hand;"
                        + "-fx-padding: 0 4 2 4;"
        );
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(closeBtn.getStyle()
                .replace("-fx-background-color: transparent;",
                        "-fx-background-color: " + CLR_CLOSE_HOVER + ";")));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(closeBtn.getStyle()
                .replace("-fx-background-color: " + CLR_CLOSE_HOVER + ";",
                        "-fx-background-color: transparent;")));

        HBox header = new HBox(8, bellIcon, titleLbl, spacer, closeBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        // Divider
        Separator divider = new Separator();
        divider.setStyle("-fx-background-color: rgba(231,76,60,0.25); -fx-padding: 2 0;");

        // Price row
        Label priceKey = new Label("Giá mới:");
        priceKey.setStyle("-fx-text-fill: " + CLR_TEXT_SECONDARY + "; -fx-font-size: 12px;");

        Label priceVal = new Label(String.format("%,d VNĐ", newPrice));
        priceVal.setStyle(
                "-fx-text-fill: " + CLR_TEXT_PRIMARY + ";"
                        + "-fx-font-size: 14px;"
                        + "-fx-font-weight: bold;"
        );

        HBox priceRow = new HBox(8, priceKey, priceVal);
        priceRow.setAlignment(Pos.CENTER_LEFT);

        // Auction ID row
        Label auctionKey = new Label("Phiên:");
        auctionKey.setStyle("-fx-text-fill: " + CLR_TEXT_SECONDARY + "; -fx-font-size: 11px;");

        Label auctionVal = new Label(auctionId);
        auctionVal.setStyle("-fx-text-fill: " + CLR_TEXT_SECONDARY + "; -fx-font-size: 11px;");

        HBox auctionRow = new HBox(6, auctionKey, auctionVal);
        auctionRow.setAlignment(Pos.CENTER_LEFT);

        // Hint
        Label hint = new Label("Vào phiên để đặt giá lại →");
        hint.setStyle(
                "-fx-text-fill: " + CLR_ACCENT + ";"
                        + "-fx-font-size: 11px;"
                        + "-fx-cursor: hand;"
        );

        VBox card = new VBox(10, header, divider, priceRow, auctionRow, hint);
        card.setPadding(new Insets(14, 16, 14, 12));
        card.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(card, Priority.ALWAYS);

        return card;
    }

    private static SequentialTransition buildDismissSequence(HBox root, Popup popup) {
        PauseTransition pause = new PauseTransition(Duration.seconds(DISPLAY_SECONDS));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(FADE_OUT_MILLIS), root);
        fadeOut.setToValue(0);

        TranslateTransition slideOut = new TranslateTransition(Duration.millis(FADE_OUT_MILLIS), root);
        slideOut.setToX(TOAST_WIDTH + 40);
        slideOut.setInterpolator(Interpolator.EASE_IN);

        ParallelTransition exit = new ParallelTransition(fadeOut, slideOut);
        exit.setOnFinished(e -> popup.hide());

        return new SequentialTransition(pause, exit);
    }

    private static void dismissImmediately(HBox root, Popup popup) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), root);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> popup.hide());
        fadeOut.play();
    }


    private static void positionPopup(Popup popup, HBox root, Stage owner) {
        double toastH = root.getHeight() > 0 ? root.getHeight() : 110;
        double x = owner.getX() + owner.getWidth()  - TOAST_WIDTH - MARGIN;
        double y = owner.getY() + owner.getHeight() - toastH     - STAGE_BOTTOM_GAP;
        popup.setX(x);
        popup.setY(y);
    }


    /**
     * Resolves the primary application stage.
     * Prefers {@code MainApplication.primalStage}; falls back to scanning
     * {@link Window#getWindows()} for any showing stage.
     */
    private static Stage resolveOwnerStage() {
        try {
            Stage primary = gui.MainApplication.primalStage;
            if (primary != null && primary.isShowing()) return primary;
        } catch (Exception ignored) {
        }
        return Window.getWindows().stream()
                .filter(w -> w instanceof Stage && w.isShowing())
                .map(w -> (Stage) w)
                .findFirst()
                .orElse(null);
    }
}