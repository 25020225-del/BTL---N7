package gui.widget.item;

import gui.process.AnimateEffect;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.function.Consumer;

/**
 * Authoritative system operation control card. Augments the base item template with
 * concrete administrative command triggers, structural signaling pathways, and localized
 * state-transition interception mechanisms.
 */
public class MinimalItemAdmin extends MinimalItem {

    private final Button btnShowItem = new Button("Show Item");
    private final Button btnApprove = new Button("Approve");
    private final Button btnReject = new Button("Reject");
    private final Button btnCancel = new Button("Cancel");

    /**
     * Constructs an administrative action capsule and enforces spatial node scaling logic
     * based on the lifecycle state parameter.
     *
     * @param id     the canonical identity key mapping to the underlying auction instance
     * @param name   the verbal nomenclature of the underlying asset
     * @param status the operational state machine token of the auction session
     * @param price  the localized numeric metric threshold representing current bids
     */
    public MinimalItemAdmin(String id, String name, String status, long price) {
        super(id);
        Label lblName = new Label(name);
        Label lblStatus = new Label(status);
        Label lblPrice = new Label(Long.toString(price));
        this.setUserData(id + name + price);
        this.setPrefSize(260, 100);
        this.getChildren().addAll(lblStatus, lblName, lblPrice);

        switch (status) {
            case "PENDING" -> AnimateEffect.hideNode(btnCancel);
            case "RUNNING" -> {
                AnimateEffect.hideNode(btnApprove);
                AnimateEffect.hideNode(btnReject);
            }
        }
    }

    /**
     * Attaches authoritative functional interaction hooks to structural buttons.
     * Enforces immediate runtime containment dismissal upon command routing to prevent multi-click races.
     *
     * @param id      the canonical destination resource mapping token
     * @param command the upstream functional event dispatcher context handling administration operations
     */
    public void addAdminOptions(String id, Consumer<String> command) {
        btnApprove.setStyle("-fx-background-color: green; -fx-text-fill: white;");
        btnReject.setStyle("-fx-background-color: red; -fx-text-fill: white;");
        btnCancel.setStyle("-fx-background-color: red; -fx-text-fill: white;");

        btnShowItem.setOnAction(event -> command.accept("SHOW_AUCTION"));

        btnApprove.setOnAction(e -> {
            command.accept("APPROVE_AUCTION");
            hideItem();
        });

        btnReject.setOnAction(e -> {
            command.accept("REJECT_AUCTION");
            hideItem();
        });

        btnCancel.setOnAction(e -> {
            command.accept("CANCEL_AUCTION");
            hideItem();
        });

        HBox btnGroup = new HBox(10, btnApprove, btnReject);
        this.getChildren().addAll(btnShowItem, btnGroup, btnCancel);
    }

    private void hideItem() {
        AnimateEffect.hideNode(this);
    }
}