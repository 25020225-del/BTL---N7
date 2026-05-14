package gui.widget;

import client.network.NetworkService;
import gui.MainApplication;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * A custom UI widget representing a user for administrator management.
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
                NetworkService.sendMessage("UNBLOCK_USER", id);
                this.setDisable(true);
            });
        } else {
            btnAction = new Button("Block");
            btnAction.setStyle("-fx-background-color: red; -fx-text-fill: white;");
            btnAction.setOnAction(e -> {
                NetworkService.sendMessage("BLOCK_USER", id);
                this.setDisable(true);
            });
        }

        HBox infoBox = new HBox(20, lblUser, lblRole);
        this.getChildren().addAll(infoBox, btnAction);
    }
}
