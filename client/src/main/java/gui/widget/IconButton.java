package gui.widget;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.javafx.FontIcon;

import static utils.ConsoleColors.RED;
import static utils.ConsoleColors.RESET;

public class IconButton extends Button {

    public IconButton(String iconName, String text, String tooltipText) {
        super(text);
        setIcon(iconName);
        if (tooltipText != null && !tooltipText.isEmpty()) {
            this.setTooltip(new Tooltip(tooltipText));
        }
        this.getStyleClass().add("custom-icon-button");
    }
    public IconButton(String iconName, String text, String tooltipText, String classIconButton) {
        this(iconName, text, tooltipText);
        this.getStyleClass().add(classIconButton);
    }
    public void setIcon(String iconName) {
        FontIcon fontIcon = new FontIcon();
        try {
            fontIcon.setIconLiteral(iconName);
        } catch (Exception e) {
            System.out.println("[Error]: " + RED + "Could not find icon: " + iconName + RESET);
            fontIcon.setIconLiteral("mdi2c-crosshairs-question");
        }
        fontIcon.setIconSize(30);
        this.setGraphic(fontIcon);
    }
}
