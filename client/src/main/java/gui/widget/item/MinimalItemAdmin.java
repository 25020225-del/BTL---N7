package gui.widget.item;

import gui.process.AnimateEffect;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.function.Consumer;

public class MinimalItemAdmin extends MinimalItem {
    // Initialize UI components
    private Button btnShowItem = new Button("Show Item");
    private Button btnApprove = new Button("Approve");
    private Button btnReject = new Button("Reject");
    private Button btnCancel = new Button("Cancel");

    public MinimalItemAdmin(String id, String name, String status, long price) {
        super(id);
        Label lblName = new Label(name);
        Label lblStatus = new Label(status);
        Label lblPrice = new Label(Long.toString(price));
        this.setUserData(id + name + price);
        this.setPrefSize(260, 100);
        this.getChildren().addAll(lblStatus, lblName, lblPrice);
        switch (status) {
            case "PENDING" -> {
                AnimateEffect.hideNode(btnCancel);
            }
            case "RUNNING" -> {
                AnimateEffect.hideNode(btnApprove);
                AnimateEffect.hideNode(btnReject);
            }
        }
    }

    public void addAdminOptions(String id, Consumer<String> command) {

        // Apply inline styling
        btnApprove.setStyle("-fx-background-color: green; -fx-text-fill: white;");
        btnReject.setStyle("-fx-background-color: red; -fx-text-fill: white;");
        btnCancel.setStyle("-fx-background-color: red; -fx-text-fill: white;");

        // Open item detail
        btnShowItem.setOnAction(event -> {
            command.accept("SHOW_AUCTION");
        });

        // Attach event handlers for approval and rejection
        btnApprove.setOnAction(e -> {
            command.accept("APPROVE_AUCTION");
            hideItem(); // Disable the widget to prevent multiple submissions
        });

        btnReject.setOnAction(e -> {
            command.accept("REJECT_AUCTION");
            hideItem(); // Disable the widget to prevent multiple submissions
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
