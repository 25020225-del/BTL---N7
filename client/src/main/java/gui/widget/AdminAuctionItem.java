package gui.widget;

import client.service.AdminService;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * A custom UI widget representing an auction item pending administrator approval.
 *
 * <p>Displays the auction item name and provides "Approve" / "Reject" action buttons.
 * Upon clicking either button, the widget is disabled to prevent duplicate submissions.</p>
 *
 * <p><b>FIX (SRP):</b> The original implementation called {@code NetworkService.sendMessage()}
 * directly inside the widget, coupling a UI component to the network layer. Actions now
 * delegate to {@link AdminService}, which is the correct service-layer boundary.</p>
 *
 * <p><b>FIX:</b> Removed the unused {@code import gui.MainApplication} statement.</p>
 */
public class AdminAuctionItem extends VBox {

    /**
     * Constructs an AdminAuctionItem widget.
     *
     * @param auctionId The unique identifier of the pending auction session.
     * @param itemName  The display name of the item being auctioned.
     */
    public AdminAuctionItem(String auctionId, String itemName) {
        super(10);
        this.setStyle("-fx-border-color: #aaa; -fx-padding: 10; -fx-background-color: white;");

        Label  lblName    = new Label("Item: " + itemName);
        Button btnApprove = new Button("Approve");
        Button btnReject  = new Button("Reject");

        btnApprove.setStyle("-fx-background-color: green; -fx-text-fill: white;");
        btnReject.setStyle("-fx-background-color: red;   -fx-text-fill: white;");

        btnApprove.setOnAction(e -> {
            AdminService.approveAuction(auctionId); // FIX: delegated to AdminService
            this.setDisable(true);
        });

        btnReject.setOnAction(e -> {
            AdminService.rejectAuction(auctionId); // FIX: delegated to AdminService
            this.setDisable(true);
        });

        HBox btnGroup = new HBox(10, btnApprove, btnReject);
        this.getChildren().addAll(lblName, btnGroup);
    }
}
