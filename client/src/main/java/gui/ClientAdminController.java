package gui;

import gui.widget.IconButton;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class ClientAdminController {

    private Parent mainView;

    @FXML
    private VBox mainDock;
    @FXML private VBox mainViewController;

    @FXML private TilePane mainTilePane;

    private IconButton account = new IconButton("mdi2a-account", "Hello Admin", "Account", "special-button");
    private IconButton toggleList = new IconButton("mdi2m-menu", "List", "List", "special-button");
    private IconButton accountList = new IconButton("mdi2a-account-box-multiple-outline", "Account", "Account", "special-button");
    private IconButton itemList = new IconButton("mdi2a-archive-settings-outline", "Item", "Item", "special-button");

    public ClientAdminController() throws IOException {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("/gui/MainView.fxml"));
        loader.setController(this);
        mainView = loader.load();
        MainApplication.setNewScene(mainView);
    }

    private void setMainDock() {
        mainDock.getChildren().add(account);
        mainDock.getChildren().addFirst(itemList);
        mainDock.getChildren().addFirst(accountList);
        mainDock.getChildren().addFirst(toggleList);

        toggleList.setUserData(true);
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
    }
    private void setMainViewController() {
    }

    public void start() {
        setMainDock();
        setMainViewController();
    }
}
