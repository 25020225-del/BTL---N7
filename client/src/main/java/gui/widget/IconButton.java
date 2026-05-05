package gui.widget;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.javafx.FontIcon;

import static utils.ConsoleColors.*;

/**
 * A custom JavaFX {@link Button} that seamlessly integrates a scalable {@link FontIcon}
 * from the Ikonli library. This widget simplifies the creation of standard UI buttons
 * with built-in icons, text labels, tooltips, and predefined CSS styling.
 */
public class IconButton extends Button {

    /**
     * Constructs an IconButton with standard default styling.
     *
     * @param iconName    The literal string identifier for the Ikonli icon (e.g., "mdi2a-account").
     * @param text        The text label to be displayed on the button.
     * @param tooltipText The descriptive text shown when hovering over the button.
     *                    If {@code null} or empty, no tooltip is assigned.
     */
    public IconButton(String iconName, String text, String tooltipText) {
        super(text);
        setIcon(iconName);
        if (tooltipText != null && !tooltipText.isEmpty()) {
            this.setTooltip(new Tooltip(tooltipText));
        }
        this.getStyleClass().add("custom-icon-button");
    }

    /**
     * Constructs an IconButton with an additional, specific CSS class for custom styling.
     *
     * @param iconName        The literal string identifier for the Ikonli icon.
     * @param text            The text label to be displayed on the button.
     * @param tooltipText     The descriptive text shown when hovering over the button.
     * @param classIconButton The name of the additional CSS style class to apply to this button.
     */
    public IconButton(String iconName, String text, String tooltipText, String classIconButton) {
        this(iconName, text, tooltipText);
        this.getStyleClass().add(classIconButton);
    }

    /**
     * Resolves and sets the graphic icon for this button using the Ikonli library.
     * If the provided icon name is invalid or cannot be found, a fallback icon
     * ("mdi2c-crosshairs-question") is automatically applied to prevent UI layout breaking.
     *
     * @param iconName The literal string identifier for the target icon.
     */
    public void setIcon(String iconName) {
        FontIcon fontIcon = new FontIcon();
        try {
            fontIcon.setIconLiteral(iconName);
        } catch (Exception e) {
            System.out.println("[Error]: " + RED + "Could not find icon: " + iconName + RESET);
            // Fallback icon when the specified literal is incorrect
            fontIcon.setIconLiteral("mdi2c-crosshairs-question");
        }
        fontIcon.setIconSize(30);
        this.setGraphic(fontIcon);
    }
}