package gui.widget;

import client.service.AdminService;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * JavaFX presentation widget representing an auction item pending administrative validation.
 * Delegates trigger events directly to service layer facades.
 */
public class AdminAuctionItem extends VBox {

    /**
     * Instantiates an admin auction enforcement card container.
     *
     * @param auctionId unique target identity key matching the persistent session row
     * @param itemName  display metadata title of the product
     */
    public AdminAuctionItem(String auctionId, String itemName) {
        super(10);
        this.setStyle("-fx-border-color: #aaa; -fx-padding: 10; -fx-background-color: white;");

        Label lblName = new Label("Item: " + itemName);
        Button btnApprove = new Button("Approve");
        Button btnReject = new Button("Reject");

        btnApprove.setStyle("-fx-background-color: green; -fx-text-fill: white;");
        btnReject.setStyle("-fx-background-color: red;   -fx-text-fill: white;");

        btnApprove.setOnAction(e -> {
            AdminService.approveAuction(auctionId);
            this.setDisable(true);
        });

        btnReject.setOnAction(e -> {
            AdminService.rejectAuction(auctionId);
            this.setDisable(true);
        });

        this.getChildren().addAll(lblName, btnApprove, btnReject);
    }
}