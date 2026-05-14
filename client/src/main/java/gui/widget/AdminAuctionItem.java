package gui.widget;

import client.network.NetworkService;
import gui.MainApplication;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * A custom UI widget representing an auction item pending administrator approval.
 * This component displays the item's name and provides actionable buttons for an
 * admin to either approve or reject the auction session.
 */
public class AdminAuctionItem extends VBox {

    /**
     * Constructs an AdminAuctionItem widget.
     *
     * @param id   The unique identifier of the auction session.
     * @param name The name of the item being auctioned.
     */
    public AdminAuctionItem(String id, String name) {
        // Initialize layout and base styling
        super(10);
        this.setStyle("-fx-border-color: #aaa; -fx-padding: 10; -fx-background-color: white;");

        // Initialize UI components
        Label lblName = new Label("Item: " + name);
        Button btnApprove = new Button("Approve");
        Button btnReject = new Button("Reject");

        // Apply inline styling
        btnApprove.setStyle("-fx-background-color: green; -fx-text-fill: white;");
        btnReject.setStyle("-fx-background-color: red; -fx-text-fill: white;");

        // Attach event handlers for approval and rejection
        btnApprove.setOnAction(e -> {
            NetworkService.sendMessage("APPROVE_AUCTION", id);
            this.setDisable(true); // Disable the widget to prevent multiple submissions
        });

        btnReject.setOnAction(e -> {
            NetworkService.sendMessage("REJECT_AUCTION", id);
            this.setDisable(true); // Disable the widget to prevent multiple submissions
        });

        HBox btnGroup = new HBox(10, btnApprove, btnReject);
        this.getChildren().addAll(lblName, btnGroup);
    }
}