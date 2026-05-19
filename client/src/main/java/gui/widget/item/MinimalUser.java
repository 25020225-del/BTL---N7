package gui.widget.item;

import client.network.NetworkService;
import client.service.AdminService;
import gui.process.AnimateEffect;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.function.Consumer;

public class MinimalUser extends MinimalItem{
    private Consumer<String> command;
    private Label lblUser;
    private Label lblRole;
    private boolean blocked = false;
    Button btnAction = new Button();
    public MinimalUser(String id, String username, String name, String role, boolean isBlocked) {
        super(id, username, "");
        this.setUserData(id+name+role+isBlocked);
        this.setPrefSize(260,100);
        lblUser = new Label("User: " + name + " (@" + username + ")");
        lblRole = new Label("Role: " + role);
        blocked = isBlocked;
        setBlockButton(blocked);
        btnAction.setOnAction(event -> {
            if (command != null){
                command.accept(blocked ? AdminService.BLOCK_USER : AdminService.UNBLOCK_USER);
                blocked = !blocked;
                AnimateEffect.pauseNode(btnAction,2);
                setBlockButton(blocked);
            }
        });

        HBox infoBox = new HBox(20, lblUser, lblRole);
        this.getChildren().addAll(infoBox, btnAction);
    }
    private void setBlockButton(boolean blocked) {
        if (blocked) {
            btnAction.setText("Unblock");
            btnAction.setStyle("-fx-background-color: green; -fx-text-fill: white;");
        }
        else {
            btnAction.setText("Block");
            btnAction.setStyle("-fx-background-color: red; -fx-text-fill: white;");
        }
    }
    public void setCommand(Consumer<String> command) {
        this.command = command;
    }
}
