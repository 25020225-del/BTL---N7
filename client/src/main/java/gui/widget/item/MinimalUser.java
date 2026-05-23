package gui.widget.item;

import gui.process.AnimateEffect;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.function.Consumer;

/**
 * A custom widget card for displaying a user account in the admin panel,
 * with an inline Block/Unblock action button.
 *
 * <p>The button label and action toggle between "Block" and "Unblock" with each click.
 * The widget is throttled with a 2-second cooldown to prevent rapid double-clicks.</p>
 *
 * <p><b>FIX (Logic Bug):</b> The original code had the block/unblock command inverted.
 * {@code command.accept(blocked ? AdminService.BLOCK_USER : AdminService.UNBLOCK_USER)}
 * was sending {@code BLOCK_USER} when the user was <em>already</em> blocked, and vice versa.
 * Fixed to send the correct command for the <em>next desired state</em>.</p>
 */
public class MinimalUser extends MinimalItem {

    private Consumer<String> onActionCommand;
    private boolean isCurrentlyBlocked;
    private final Button btnAction = new Button();

    /**
     * Constructs a MinimalUser card widget.
     *
     * @param id        The user's unique identifier.
     * @param username  The user's login username.
     * @param name      The user's display name.
     * @param role      The user's role (e.g., "USER", "ADMIN").
     * @param isBlocked Whether the user account is currently blocked.
     */
    public MinimalUser(String id, String username, String name, String role, boolean isBlocked) {
        super(id);
        this.setUserData(id + name + role + isBlocked);
        this.setPrefSize(260, 100);
        this.isCurrentlyBlocked = isBlocked;

        Label lblUser = new Label("User: " + name + " (@" + username + ")");
        Label lblRole = new Label("Role: " + role);

        applyBlockButtonState(isBlocked);

        btnAction.setOnAction(event -> {
            if (onActionCommand == null) return;

            String command = isCurrentlyBlocked
                    ? "UNBLOCK_USER"
                    : "BLOCK_USER";

            onActionCommand.accept(command);
            isCurrentlyBlocked = !isCurrentlyBlocked;
            applyBlockButtonState(isCurrentlyBlocked);
            AnimateEffect.pauseNode(btnAction, 2);
        });

        HBox infoBox = new HBox(20, lblUser, lblRole);
        this.getChildren().addAll(infoBox, btnAction);
    }

    /**
     * Sets the action handler invoked when the user clicks the Block/Unblock button.
     * The handler receives the command string ({@code "BLOCK_USER"} or {@code "UNBLOCK_USER"}).
     *
     * @param command A {@link Consumer} that accepts the action command string.
     */
    public void setCommand(Consumer<String> command) {
        this.onActionCommand = command;
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Updates the button's label and color to reflect the intended next action.
     *
     * @param currentlyBlocked {@code true} if the user is currently blocked.
     */
    private void applyBlockButtonState(boolean currentlyBlocked) {
        if (currentlyBlocked) {
            btnAction.setText("Unblock");
            btnAction.setStyle("-fx-background-color: green; -fx-text-fill: white;");
        } else {
            btnAction.setText("Block");
            btnAction.setStyle("-fx-background-color: red; -fx-text-fill: white;");
        }
    }
}
