package gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gui.process.AlertHelper;
import gui.widget.AdminAuctionItem;
import gui.widget.IconButton;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import model.user.User;

import java.io.IOException;

/**
 * Controller dedicated to the Administrator role.
 * Manages the admin dashboard, handles pending auction approvals,
 * and monitors the overall system statistics.
 */
public class ClientAdminController {
    private static final Logger log = LoggerFactory.getLogger(ClientAdminController.class);

    private Parent mainView;
    private Parent adminView;
    private Parent tableView;
    private User currentAdmin;

    @FXML
    private VBox mainDock;
    @FXML
    private VBox mainViewController;

    @FXML
    private HBox searchBarContainer;
    @FXML
    private TilePane mainTilePane;
    @FXML
    private TextField searchField;
    @FXML
    private Button searchButton;

    private IconButton account = new IconButton("mdi2a-account", "Hello Admin", "Account", "special-button");
    private IconButton toggleList = new IconButton("mdi2m-menu", "List", "List", "special-button");
    private IconButton accountList = new IconButton("mdi2a-account-box-multiple-outline", "Accounts", "Manage Accounts", "special-button");
    private IconButton itemList = new IconButton("mdi2a-archive-settings-outline", "Items", "Manage Items", "special-button");

    /**
     * Initializes the Admin Controller and loads the required FXML layouts.
     *
     * @param user The currently authenticated administrator instance.
     * @throws IOException If the corresponding FXML files cannot be loaded.
     */
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

    /**
     * Configures the side navigation dock, attaching corresponding action events
     * to the menu buttons.
     */
    private void setMainDock() {
        Region region = new Region();
        Separator separator = new Separator();
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
            for (Node k : mainDock.getChildren()) {
                if (k instanceof Button) {
                    Button b = (Button) k;
                    if ((boolean) toggleList.getUserData()) {
                        b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    } else {
                        b.setContentDisplay(ContentDisplay.LEFT);
                    }
                }
            }
            toggleList.setUserData(!((boolean) toggleList.getUserData()));
        });

        itemList.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(tableView);
            log.info("Loading pending auctions...");
            // Request pending auctions from the server
            MainApplication.networkClient.sendMessage("FETCH_PENDING_AUCTIONS", "");
        });
    }

    /**
     * Sets up the listener for server responses regarding admin actions.
     */
    private void setMainViewController() {
        MainApplication.networkClient.setOnMessageReceived(response -> {
            javafx.application.Platform.runLater(() -> {
                String command = response.getCommand();

                // 1. Render the list of pending auctions
                if ("FETCH_AUCTIONS_SUCCESS".equals(command)) {
                    mainTilePane.getChildren().clear();

                    @SuppressWarnings("unchecked")
                    java.util.List<java.util.Map<String, Object>> auctions =
                            (java.util.List<java.util.Map<String, Object>>) response.getData();

                    for (java.util.Map<String, Object> data : auctions) {
                        String id = (String) data.get("id");
                        String name = (String) data.get("itemName");

                        mainTilePane.getChildren().add(new AdminAuctionItem(id, name));
                    }
                }
                // 2. Handle successful approval or rejection
                else if ("ADMIN_ACTION_SUCCESS".equals(command)) {
                    AlertHelper.showAlert(javafx.scene.control.Alert.AlertType.INFORMATION, "Success", response.getData().toString());
                    // Automatically reload the pending list after an action
                    itemList.getOnAction().handle(null);
                } else {
                    // Forward unhandled commands to the centralized ResponseDispatcher
                    new client.handler.ResponseDispatcher().dispatch(response, gui.MainApplication.networkClient);
                }
            });
        });
    }

    /**
     * Bootstraps the controller logic upon initial navigation to this view.
     */
    public void start() {
        setMainDock();
        setMainViewController();
        log.info("Admin view initialized successfully.");
    }
}