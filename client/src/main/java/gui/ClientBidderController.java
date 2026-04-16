package gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import model.Bidder;
import model.User;

import java.io.File;
import java.io.IOException;


public class ClientBidderController {
    static TilePane table = null;


    public static void start() throws IOException {
        VBox mainDock = (VBox) MainApplication.rootMainView.lookup("#mainDock");
        VBox mainViewController = (VBox) MainApplication.rootMainView.lookup("#mainViewController");

        VBox product = (VBox) WidgetFactory.createMinimalItem("Butter","30$","12 days");


        for (Node node : mainViewController.getChildren()) {
            if (node instanceof ScrollPane) {
                ScrollPane sp = (ScrollPane) node;
                if (sp.getContent() instanceof TilePane) {
                    table = (TilePane) sp.getContent();
                }
            }
        }

        AnchorPane find = (AnchorPane) mainViewController.getChildren().get(0);

        TextField searchField = (TextField) MainApplication.rootMainView.lookup("#searchField");

        Button findItem = (Button) WidgetFactory.createButton("mdi2f-file-find-outline","","Find");
        Button searchButton = (Button) MainApplication.rootMainView.lookup("#searchButton");

        findItem.setOnAction(event -> {
            AnimateEffect.fadeNode(find,!find.isVisible());
        });
        searchButton.setOnAction(event -> {
            String search = searchField.getText();
            AnimateEffect.showOrHideItem(table,search);
        });

        mainDock.getChildren().addFirst(findItem);

        table.getChildren().add(WidgetFactory.createMinimalItem("Máy xay sinh tố mèo","30000","3"));
        table.getChildren().add(WidgetFactory.createMinimalItem("Đùi gà tẩm bột chiên xù","40000","3"));
        table.getChildren().add(WidgetFactory.createMinimalItem("Máy bay đồ chơi mini","1200000","3"));
        table.getChildren().add(WidgetFactory.createMinimalItem("Thịt cừu nướng","127000","4"));
        table.getChildren().add(WidgetFactory.createMinimalItem("Mỡ lợn","80000","3"));
        table.getChildren().add(WidgetFactory.createMinimalItem("Đầu cá","35000","2"));
        System.out.println(table);
    }
}
