package gui.widget;

import gui.MainApplication;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class AdminAuctionItem extends VBox {

    public AdminAuctionItem(String id, String name) {
        // 1. Setup Layout
        super(10);
        this.setStyle("-fx-border-color: #aaa; -fx-padding: 10; -fx-background-color: white;");

        // 2. Components
        Label lblName = new Label("Item: " + name);
        Button btnApprove = new Button("Approve");
        Button btnReject = new Button("Reject");

        // Styling
        btnApprove.setStyle("-fx-background-color: green; -fx-text-fill: white;");
        btnReject.setStyle("-fx-background-color: red; -fx-text-fill: white;");

        // 3. Events
        btnApprove.setOnAction(e -> {
            MainApplication.networkClient.sendMessage("APPROVE_AUCTION", id);
            this.setDisable(true); // Vô hiệu hóa sau khi bấm
        });

        btnReject.setOnAction(e -> {
            MainApplication.networkClient.sendMessage("REJECT_AUCTION", id);
            this.setDisable(true);
        });

        HBox btnGroup = new HBox(10, btnApprove, btnReject);
        this.getChildren().addAll(lblName, btnGroup);
    }
}