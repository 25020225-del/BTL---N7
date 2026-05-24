package gui.widget;

import client.service.AdminService;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * JavaFX presentation widget representing a user account management profile card.
 * Triggers atomic structural administrative enforcement commands down to service bounds.
 */
public class AdminUserItem extends VBox {

    public AdminUserItem(String id, String username, String name, String role, boolean isBlocked) {
        super(10);
        this.setStyle("-fx-border-color: #aaa; -fx-padding: 10; -fx-background-color: white;");

        Label lblUser = new Label("User: " + name + " (@" + username + ")");
        Label lblRole = new Label("Role: " + role);

        Button btnAction;
        if (isBlocked) {
            btnAction = new Button("Unblock");
            btnAction.setStyle("-fx-background-color: green; -fx-text-fill: white;");
            btnAction.setOnAction(e -> {
                AdminService.unblockUser(id);
                this.setDisable(true);
            });
        } else {
            btnAction = new Button("Block");
            btnAction.setStyle("-fx-background-color: red; -fx-text-fill: white;");
            btnAction.setOnAction(e -> {
                AdminService.blockUser(id);
                this.setDisable(true);
            });
        }

        this.getChildren().addAll(lblUser, lblRole, btnAction);
    }
}