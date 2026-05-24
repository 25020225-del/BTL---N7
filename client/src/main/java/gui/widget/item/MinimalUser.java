package gui.widget.item;

import gui.process.AnimateEffect;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.function.Consumer;

/**
 * Administrative identity representation capsule. Embeds active status control directives,
 * authoritative state mutation routing hooks, and temporary input throttle boundaries.
 */
public class MinimalUser extends MinimalItem {

    private Consumer<String> onActionCommand;
    private boolean isCurrentlyBlocked;
    private boolean isCurrentlyGood;

    private final Button btnAction = new Button();
    private final Button btnSetAtGood = new Button();

    /**
     * Allocates an administrative user profile component and filters interactive
     * action components depending on privilege hierarchy parameters.
     *
     * @param id        the absolute server-side identifier of the target actor
     * @param username  the canonical lookup account name signature
     * @param name      the descriptive identity string of the individual
     * @param role      the systemic security context or authorization claim level
     * @param isBlocked the foundational constraint indicator specifying account lock states
     * @param isGood    the premium programmatic indicator specifying behavioral status modifiers
     */
    public MinimalUser(String id, String username, String name, String role, boolean isBlocked, boolean isGood) {
        super(id);

        if ("Admin".equalsIgnoreCase(role.trim())) {
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
     * Registers the structural command relay callback handling actor privilege changes.
     *
     * @param command the execution context mapping functionally to remote server actions
     */
    public void setCommand(Consumer<String> command) {
        this.onActionCommand = command;
    }

    private void applyBlockButtonState(boolean currentlyBlocked) {
        if (currentlyBlocked) {
            btnAction.setText("Unblock");
            btnAction.setStyle("-fx-background-color: #2ECC71; -fx-text-fill: white; -fx-cursor: hand;");
        } else {
            btnAction.setText("Block");
            btnAction.setStyle("-fx-background-color: #E74C3C; -fx-text-fill: white; -fx-cursor: hand;");
        }
    }

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