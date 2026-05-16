package gui.widget.item;

import client.network.NetworkService;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

public class MinimalItemAdmin extends MinimalItem {

    public MinimalItemAdmin(String id, String name, String price) {
        super(id, name, price);
    }

    public void addAdminOptions(String id) {
        // Initialize UI components
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
        this.getChildren().add(btnGroup);
    }
}
