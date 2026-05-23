package gui.widget.item;

import gui.process.AnimateEffect;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.function.Consumer;

/**
 * A custom widget card for displaying a user account in the admin panel,
 * with inline Block/Unblock and Set Good/Remove Good action buttons.
 */
public class MinimalUser extends MinimalItem {

    private Consumer<String> onActionCommand;
    private boolean isCurrentlyBlocked;
    private boolean isCurrentlyGood;

    private final Button btnAction = new Button();
    private final Button btnSetAtGood = new Button();

    /**
     * Constructs a MinimalUser card widget.
     *
     * @param id        The user's unique identifier.
     * @param username  The user's login username.
     * @param name      The user's display name.
     * @param role      The user's role (e.g., "USER", "ADMIN").
     * @param isBlocked Whether the user account is currently blocked.
     * @param isGood    Whether the user has a "Good" status reputation.
     */
    public MinimalUser(String id, String username, String name, String role, boolean isBlocked, boolean isGood) {
        super(id);
        if(role.trim().equals("Admin")){
            btnSetAtGood.setDisable(true);
            btnSetAtGood.setVisible(false);
            btnAction.setDisable(true);
            btnAction.setVisible(false);
        }

        this.setUserData(id);
        this.setPrefSize(260, 100);

        this.isCurrentlyBlocked = isBlocked;
        this.isCurrentlyGood = isGood;

        Label lblUser = new Label("User: " + name + " (@" + username + ")");
        Label lblRole = new Label("Role: " + role);

        applyBlockButtonState(isBlocked);
        applyGoodButtonState(isGood);

        btnAction.setOnAction(event -> {
            if (onActionCommand == null) return;

            String command = isCurrentlyBlocked ? "UNBLOCK_USER" : "BLOCK_USER";

            onActionCommand.accept(command);
            isCurrentlyBlocked = !isCurrentlyBlocked;
            applyBlockButtonState(isCurrentlyBlocked);
            AnimateEffect.pauseNode(btnAction, 2);
        });

        btnSetAtGood.setOnAction(event -> {
            if (onActionCommand == null) return;

            onActionCommand.accept("TOGGLE_GOOD_STATUS");
            isCurrentlyGood = !isCurrentlyGood;
            applyGoodButtonState(isCurrentlyGood);
            AnimateEffect.pauseNode(btnSetAtGood, 2);
        });

        HBox infoBox = new HBox(20, lblUser, lblRole);
        this.getChildren().addAll(infoBox, btnAction, btnSetAtGood);
    }

    /**
     * Sets the action handler invoked when the user clicks the action buttons.
     *
     * @param command A Consumer that accepts the action command string.
     */
    public void setCommand(Consumer<String> command) {
        this.onActionCommand = command;
    }

    // Private Helpers

    /**
     * Updates the block button's label and color to reflect the intended next action.
     */
    private void applyBlockButtonState(boolean currentlyBlocked) {
        if (currentlyBlocked) {
            btnAction.setText("Unblock");
            btnAction.setStyle("-fx-background-color: #2ECC71; -fx-text-fill: white; -fx-cursor: hand;");
        } else {
            btnAction.setText("Block");
            btnAction.setStyle("-fx-background-color: #E74C3C; -fx-text-fill: white; -fx-cursor: hand;");
        }
    }

    /**
     * Updates the Good status button's label and color to reflect the intended next action.
     */
    private void applyGoodButtonState(boolean currentlyGood) {
        if (currentlyGood) {
            btnSetAtGood.setText("Remove Good");
            btnSetAtGood.setStyle("-fx-background-color: #7F8C8D; -fx-text-fill: white; -fx-cursor: hand;");
        } else {
            btnSetAtGood.setText("Set Good");
            btnSetAtGood.setStyle("-fx-background-color: #27AE60; -fx-text-fill: white; -fx-cursor: hand;");
        }
    }
}