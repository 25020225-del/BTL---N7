package gui;

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

    private Button account = (Button) WidgetFactory.createButton("mdi2a-account","Hello Admin","Account");
    private Button toggleList = (Button) WidgetFactory.createButton("mdi2m-menu","List","List");
    private Button accountList =  (Button) WidgetFactory.createButton("mdi2a-archive-plus-outline","Account","Account");
    private Button itemList = (Button) WidgetFactory.createButton("mdi2m-menu","Item","Item");

    public ClientAdminController() throws IOException {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("/gui/MainView.fxml"));
        loader.setController(this);
        mainView = loader.load();
        MainApplication.setNewScene(mainView);
    }

    private void setMainDock() {
        mainDock.getChildren().clear();
        mainDock.getChildren().add(account);
        mainDock.getChildren().addFirst(accountList);
        mainDock.getChildren().addFirst(toggleList);
        for(Node k : mainDock.getChildren()){
            if(k instanceof Button){
                k.getStyleClass().add("special-button");
            }
        }

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
