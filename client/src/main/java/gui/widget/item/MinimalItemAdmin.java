package gui.widget.item;

import client.network.NetworkService;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

import java.util.function.Consumer;

public class MinimalItemAdmin extends MinimalItem {

    public MinimalItemAdmin(String id, String name, String price) {
        super(id, name, price);
        this.setUserData(id+name+price);
        this.setPrefSize(260,100);
    }

    public void addAdminOptions(String id, Consumer<String> command) {
        // Initialize UI components
        Button btnApprove = new Button("Approve");
        Button btnReject = new Button("Reject");

        // Apply inline styling
        btnApprove.setStyle("-fx-background-color: green; -fx-text-fill: white;");
        btnReject.setStyle("-fx-background-color: red; -fx-text-fill: white;");

        // Attach event handlers for approval and rejection
        btnApprove.setOnAction(e -> {
            command.accept("APPROVE_AUCTION");
            this.setDisable(true); // Disable the widget to prevent multiple submissions
        });

        btnReject.setOnAction(e -> {
            command.accept("REJECT_AUCTION");
            this.setDisable(true); // Disable the widget to prevent multiple submissions
        });

        HBox btnGroup = new HBox(10, btnApprove, btnReject);
        this.getChildren().add(btnGroup);
    }
}
