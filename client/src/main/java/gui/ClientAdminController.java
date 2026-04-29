package gui;

import gui.process.AlertHelper;
import gui.widget.AdminAuctionItem;
import gui.widget.IconButton;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import model.User;

import java.io.IOException;

public class ClientAdminController {

    private Parent mainView;
    private Parent adminView;
    private Parent tableView;
    private User currentAdmin;

    @FXML
    private VBox mainDock;
    @FXML private VBox mainViewController;

    @FXML private HBox searchBarContainer;
    @FXML private TilePane mainTilePane;
    @FXML private TextField searchField;
    @FXML private Button searchButton;

    private IconButton account = new IconButton("mdi2a-account", "Hello Admin", "Account", "special-button");
    private IconButton toggleList = new IconButton("mdi2m-menu", "List", "List", "special-button");
    private IconButton accountList = new IconButton("mdi2a-account-box-multiple-outline", "Account", "Account", "special-button");
    private IconButton itemList = new IconButton("mdi2a-archive-settings-outline", "Item", "Item", "special-button");

    public ClientAdminController(User user) throws IOException {
        this.currentAdmin = user;
        this.account = new IconButton("mdi2a-account", "Admin: " + user.getName(), "Account");
        FXMLLoader mainViewloader = new FXMLLoader(getClass().getResource("MainView.fxml"));
        mainViewloader.setController(this);
        mainView = mainViewloader.load();
        FXMLLoader adminViewLoader = new FXMLLoader(getClass().getResource("AdminView.fxml"));
        adminViewLoader.setController(this);
        adminView = adminViewLoader.load();
        FXMLLoader tableViewLoader = new FXMLLoader(getClass().getResource("TableView.fxml"));
        tableViewLoader.setController(this);
        tableView = tableViewLoader.load();

        MainApplication.setNewScene(mainView);
    }

    private void setMainDock() {
        Region region = new Region();
        Separator separator  = new Separator();
        VBox.setVgrow(region, Priority.ALWAYS);
        mainDock.getChildren().addAll(
                toggleList,
                accountList,
                itemList,
                separator,
                region,
                account
        );

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
        itemList.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(tableView);
            System.out.println("[System]: Loading pending auctions...");
            // Get data from server
            MainApplication.networkClient.sendMessage("FETCH_PENDING_AUCTIONS", "");
        });
    }
    private void setMainViewController() {
        // Listen to server response
        MainApplication.networkClient.setOnMessageReceived(response -> {
            javafx.application.Platform.runLater(() -> {
                String command = response.getCommand();

                // 1. Get list and Render
                if ("FETCH_AUCTIONS_SUCCESS".equals(command)) { // Use the same key as bidder
                    mainTilePane.getChildren().clear();

                    java.util.List<java.util.Map<String, Object>> auctions =
                            (java.util.List<java.util.Map<String, Object>>) response.getData();

                    for (java.util.Map<String, Object> data : auctions) {
                        String id = (String) data.get("id");
                        String name = (String) data.get("itemName");

                        mainTilePane.getChildren().add(new AdminAuctionItem(id, name));
                    }
                }
                // 2. Receive a notification of successful approval
                else if ("ADMIN_ACTION_SUCCESS".equals(command)) {
                    AlertHelper.showAlert(javafx.scene.control.Alert.AlertType.INFORMATION, "Success", response.getData().toString());
                    // Automatically reload the page after browsing
                    itemList.getOnAction().handle(null);
                }
                else {
                    // Navigate to other commands
                    new client.handler.ResponseDispatcher().dispatch(response, gui.MainApplication.networkClient);
                }
            });
        });
    }

    public void start() {
        setMainDock();
        setMainViewController();
    }
}
