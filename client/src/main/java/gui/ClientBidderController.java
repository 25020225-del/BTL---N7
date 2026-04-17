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

import static utils.ConsoleColors.*;

public class ClientBidderController {

    private static TilePane itemTable = null;

    public static void start() throws IOException {
        System.out.println("[Log]: Initializing Bidder View Components...");

        VBox mainDock = (VBox) MainApplication.rootMainView.lookup("#mainDock");
        VBox mainViewController = (VBox) MainApplication.rootMainView.lookup("#mainViewController");

        for (Node node : mainViewController.getChildren()) {
            if (node instanceof ScrollPane) {
                ScrollPane scrollPane = (ScrollPane) node;
                if (scrollPane.getContent() instanceof TilePane) {
                    itemTable = (TilePane) scrollPane.getContent();
                }
            }
        }

        if (itemTable == null) {
            System.out.println("[Error]: " + RED + "Could not find Item Table (TilePane) in UI" + RESET);
            return;
        }

        AnchorPane searchBarContainer = (AnchorPane) mainViewController.getChildren().get(0);
        TextField searchField = (TextField) MainApplication.rootMainView.lookup("#searchField");

        Button toggleSearchButton = (Button) WidgetFactory.createButton("mdi2f-file-find-outline", "", "Find");
        Button executeSearchButton = (Button) MainApplication.rootMainView.lookup("#searchButton");

        toggleSearchButton.setOnAction(event -> {
            AnimateEffect.fadeNode(searchBarContainer, !searchBarContainer.isVisible());
        });

        executeSearchButton.setOnAction(event -> {
            String keyword = searchField.getText();
            System.out.println("[Log]: Searching for: " + YELLOW + keyword + RESET);
            AnimateEffect.showOrHideItem(itemTable, keyword);
        });

        mainDock.getChildren().addFirst(toggleSearchButton);

        itemTable.getChildren().add(WidgetFactory.createMinimalItem("Máy xay sinh tố mèo","30000","3"));
        itemTable.getChildren().add(WidgetFactory.createMinimalItem("Đùi gà tẩm bột chiên xù","40000","3"));
        itemTable.getChildren().add(WidgetFactory.createMinimalItem("Máy bay đồ chơi mini","1200000","3"));
        itemTable.getChildren().add(WidgetFactory.createMinimalItem("Thịt cừu nướng","127000","4"));
        itemTable.getChildren().add(WidgetFactory.createMinimalItem("Mỡ lợn","80000","3"));
        itemTable.getChildren().add(WidgetFactory.createMinimalItem("Đầu cá","35000","2"));

        System.out.println(GREEN + "[System]: Bidder Controller started successfully. Table updated." + RESET);
    }
}