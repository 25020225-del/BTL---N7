package gui.widget.item;

import client.network.NetworkService;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class MinimalUser extends MinimalItem{
    public MinimalUser(String id, String username, String name, String role, boolean isBlocked) {
        super(id, username, "");
        this.setUserData(id+name+role+isBlocked);
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
