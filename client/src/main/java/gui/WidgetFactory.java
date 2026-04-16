package gui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.sql.SQLOutput;

public class WidgetFactory {
    public static Button createButton(String iconName, String text, String tooltip) {
        FontIcon fontIcon = new FontIcon();
        try{
            fontIcon.setIconLiteral(iconName);
        }
        catch (Exception e){
            //System.err.println("Could not find icon " + e.getMessage());
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
    public static VBox createMinimalItem(String nameString, String priceString, String dateString) {
        Label name =  new Label(nameString);
        Label price = new Label(priceString + " vnd");
        Label date = new Label(dateString+ " days");

        VBox minimalItem = new VBox();
        minimalItem.setStyle("-fx-background-radius: 10;-fx-border-style: solid;-fx-border-color: #c2c2c2;-fx-border-width: 2;-fx-border-radius: 10;");

        minimalItem.setPrefSize(260, 370);
        minimalItem.setPadding(new Insets(20));
        minimalItem.setSpacing(10);

        minimalItem.getChildren().add(name);
        minimalItem.getChildren().add(price);
        minimalItem.getChildren().add(date);
        minimalItem.getChildren().add(createButton("","ADD TO CART","ADD"));

        return minimalItem;
    }
}
