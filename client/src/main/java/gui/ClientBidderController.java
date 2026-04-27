package gui;

import gui.widget.IconButton;
import gui.widget.MinimalItem;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

import java.io.IOException;

import static utils.ConsoleColors.*;

public class ClientBidderController {

    private Parent mainView = null;

    @FXML private VBox mainDock;
    @FXML private VBox mainViewController;
    @FXML private HBox searchBarContainer;

    @FXML private TilePane mainTilePane;

    private IconButton toggleSearchButton = new IconButton("mdi2f-file-find-outline", "search", "Search","special-button");
    private IconButton account = new IconButton("mdi2a-account", "Hello Bidder", "Account","special-button");
    private IconButton toggleList = new IconButton("mdi2m-menu", "List", "List","special-button");

    @FXML private Button searchButton;
    @FXML private TextField searchField;

    public ClientBidderController() throws IOException {
        FXMLLoader fxmlMainView = new FXMLLoader(ClientBidderController.class.getResource("MainView.fxml"));
        fxmlMainView.setController(this);
        mainView = fxmlMainView.load();
        MainApplication.setNewScene(mainView);
    }

    private void setMainDock() {
        mainDock.getChildren().add(account);
        mainDock.getChildren().addFirst(toggleSearchButton);
        mainDock.getChildren().addFirst(toggleList);

        toggleList.setUserData(true);
        searchField.setOnAction(event -> {
            searchButton.getOnAction().handle(null);
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
        searchButton.setOnAction(event -> {
            String keyword = searchField.getText();
            System.out.println("[Log]: Searching for: " + YELLOW + keyword + RESET);
            AnimateEffect.showOrHideItem(mainTilePane, keyword);
        });
    }
    private void setMainViewController() {
        final long TWO_MINUTES = 2 * 60 * 1000;
        long endTime = System.currentTimeMillis() + TWO_MINUTES;
        mainTilePane.getChildren().addAll(
                new MinimalItem("Máy xay sinh tố mèo", "30000", endTime),
                new MinimalItem("Đùi gà tẩm bột chiên xù", "40000", endTime),
                new MinimalItem("Máy bay đồ chơi mini", "1200000", endTime),
                new MinimalItem("Thịt cừu nướng", "127000", endTime),
                new MinimalItem("Mỡ lợn", "80000", endTime),
                new MinimalItem("Đầu cá", "35000", endTime)
        );
    }

    public void start() throws IOException {
        setMainDock();
        setMainViewController();
        System.out.println("[Log]: Initializing Bidder View Components...");

        if (mainTilePane == null) {
            System.out.println("[Error]: " + RED + "Could not find Item Table (TilePane) in UI" + RESET);
            return;
        }
        System.out.println(GREEN + "[System]: Bidder Controller started successfully. Table updated." + RESET);
    }
}