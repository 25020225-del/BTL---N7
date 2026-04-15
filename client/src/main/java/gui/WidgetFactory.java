package gui;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.javafx.FontIcon;

import java.sql.SQLOutput;

public class WidgetFactory {
    public static Button createButton(String iconName, String text, String tooltip) {
        FontIcon fontIcon = new FontIcon();
        try{
            fontIcon.setIconLiteral(iconName);
        }
        catch (Exception e){
            System.err.println("Could not find icon " + e.getMessage());
            fontIcon.setIconLiteral("mdi2c-crosshairs-question");
        }
        fontIcon.setIconSize(30);
        Button button = new Button();
        button.setGraphic(fontIcon);
        button.setGraphicTextGap(8);
        button.setText(text);
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }
}
