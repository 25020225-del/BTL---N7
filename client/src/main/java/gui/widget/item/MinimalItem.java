package gui.widget.item;

import client.network.NetworkService;
import gui.process.CropImage;
import gui.widget.CountdownClock;
import gui.widget.IconButton;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * A custom UI widget representing a condensed view of an auction item.
 * Displayed primarily in the Marketplace grid layout.
 */
public class MinimalItem extends VBox {
    protected IconButton auctionButton;

    public MinimalItem(String id){
        this.setPrefSize(260, 370);
        this.setPadding(new Insets(20));
        this.setSpacing(10);
        this.setStyle(
                "-fx-background-radius: 10; " +
                        "-fx-border-style: solid; " +
                        "-fx-border-color: #c2c2c2; " +
                        "-fx-border-width: 2; " +
                        "-fx-border-radius: 10; " +
                        "-fx-background-color: white;"
        );
        this.setId(id);
    }

}