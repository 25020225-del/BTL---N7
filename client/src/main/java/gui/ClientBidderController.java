package gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
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

        HBox searchBarContainer = (HBox) mainViewController.getChildren().get(0);
        TextField searchField = (TextField) MainApplication.rootMainView.lookup("#searchField");

        Button toggleList = (Button) WidgetFactory.createButton("mdi2m-menu","List","List");
        toggleList.setUserData(true);
        Button toggleSearchButton = (Button) WidgetFactory.createButton("mdi2f-file-find-outline", "search", "Search");
        Button account = (Button) WidgetFactory.createButton("mdi2a-account","Account","Account");


        Button executeSearchButton = (Button) MainApplication.rootMainView.lookup("#searchButton");
        searchField.setOnAction(event -> {
            executeSearchButton.getOnAction().handle(null);
        });
        toggleList.setOnAction(event -> {
            for(Node k : mainDock.getChildren()) {
                if (k instanceof Button) {
                    Button b = (Button) k;
                    if ((boolean)  toggleList.getUserData()) {
                        b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    }
                    else {
                        b.setContentDisplay(ContentDisplay.LEFT);
                    }
                }
            }
            toggleList.setUserData(!((boolean) toggleList.getUserData()));
        });
        toggleSearchButton.setOnAction(event -> {
            AnimateEffect.fadeNode(searchBarContainer, !searchBarContainer.isVisible());
        });

        executeSearchButton.setOnAction(event -> {
            String keyword = searchField.getText();
            System.out.println("[Log]: Searching for: " + YELLOW + keyword + RESET);
            AnimateEffect.showOrHideItem(itemTable, keyword);
        });

        mainDock.getChildren().addFirst(toggleSearchButton);
        mainDock.getChildren().addFirst(toggleList);
        mainDock.getChildren().add(account);

        for(Node k : mainDock.getChildren()){
            if(k instanceof Button){
                k.getStyleClass().add("special-button");
            }
        }

        itemTable.getChildren().add(WidgetFactory.createMinimalItem("Máy xay sinh tố mèo","30000","3"));
        itemTable.getChildren().add(WidgetFactory.createMinimalItem("Đùi gà tẩm bột chiên xù","40000","3"));
        itemTable.getChildren().add(WidgetFactory.createMinimalItem("Máy bay đồ chơi mini","1200000","3"));
        itemTable.getChildren().add(WidgetFactory.createMinimalItem("Thịt cừu nướng","127000","4"));
        itemTable.getChildren().add(WidgetFactory.createMinimalItem("Mỡ lợn","80000","3"));
        itemTable.getChildren().add(WidgetFactory.createMinimalItem("Đầu cá","35000","2"));

        System.out.println("[System]:" + GREEN + " Bidder Controller started successfully. Table updated." + RESET);
    }
}