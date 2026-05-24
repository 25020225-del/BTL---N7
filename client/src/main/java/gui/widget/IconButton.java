package gui.widget;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom graphical button wrapper integrating scalable vector typography glyphs.
 * Simplifies presentation layout declaration by combining icon resolution,
 * contextual tooltips, and declarative styling classes.
 */
public class IconButton extends Button {

    private static final Logger log = LoggerFactory.getLogger(IconButton.class);
    private static final String FALLBACK_ICON = "mdi2c-crosshairs-question";

    /**
     * Constructs an IconButton with standard default styling rules.
     *
     * @param iconName    the literal string identifier for the target vector icon
     * @param text        the descriptive text label to display on the button
     * @param tooltipText the explanatory text block shown when hovering over the element bounds
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
     * Constructs an IconButton reinforced with an additional custom CSS style class identifier.
     */
    public IconButton(String iconName, String text, String tooltipText, String classIconButton) {
        this(iconName, text, tooltipText);
        this.getStyleClass().add(classIconButton);
    }

    /**
     * Resolves and binds a vector icon onto the component's graphical node space.
     * Automatically applies a diagnostic fallback glyph if the targeted asset identifier cannot be resolved.
     *
     * @param iconName the unique literal mapping key within the icon provider registry
     */
    public void setIcon(String iconName) {
        FontIcon fontIcon = new FontIcon();
        try {
            fontIcon.setIconLiteral(iconName);
        } catch (Exception e) {
            log.error("Failed to resolve visual asset identifier: '{}'. Applying fallback diagnostic glyph.", iconName);
            fontIcon.setIconLiteral(FALLBACK_ICON);
        }
        fontIcon.setIconSize(30);
        this.setGraphic(fontIcon);
    }
}