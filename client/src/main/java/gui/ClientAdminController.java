package gui;

import client.handler.AuctionEventBus;
import client.network.NetworkService;
import client.service.AdminService;
import gui.process.RemoveEventBus;
import gui.userController.table.TableControllerAdmin;
import gui.widget.item.MinimalItemAdmin;
import gui.widget.item.MinimalUser;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import model.user.Admin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gui.process.AlertHelper;
import gui.widget.IconButton;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;
import model.user.User;

import javafx.event.ActionEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Controller dedicated to the Administrator role.
 * Manages the admin dashboard, handles pending auction approvals,
 * and monitors the overall system statistics.
 */
public class ClientAdminController {
    private static final Logger log = LoggerFactory.getLogger(ClientAdminController.class);

    private Parent mainView;
    private Parent settingsView;

    @FXML private VBox mainDock;
    @FXML private VBox mainViewController;

    private TableControllerAdmin tableView;

    private IconButton account;
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
        this.account = new IconButton("mdi2a-account", "Admin: " + user.getName(), "Account","special-button");

        FXMLLoader mainViewloader = new FXMLLoader(getClass().getResource("MainView.fxml"));
        mainViewloader.setController(this);
        mainView = mainViewloader.load();

        tableView = new TableControllerAdmin();

        FXMLLoader settingsLoader = new FXMLLoader(getClass().getResource("SettingsView.fxml"));
        settingsLoader.setController(this);
        settingsView = settingsLoader.load();

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
            mainViewController.getChildren().add(tableView.getParent());
            log.info("Loading pending auctions...");
            // Request pending auctions from the server
            AdminService.fetchPendingAuctions();
        });

        accountList.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(tableView.getParent());
            log.info("Loading user list...");
            AdminService.fetchUsers();
        });
        account.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(settingsView);
        });
    }

    @FXML
    private void handleBackToMarketplace(){
        itemList.fire();
    }

    @FXML
    private void handleSignOut(){
        RemoveEventBus.forUser();
        log.info("User \"{}\" is signing out.", "Admin");
        AdminService.logout();
        MainApplication.setNewScene(MainApplication.rootLogin);
    }

    /**
     * Sets up the listener for server responses regarding admin actions.
     */
    private void setMainViewController() {
        AuctionEventBus.addListener("ADMIN_ACTION_SUCCESS", event -> {
            Platform.runLater(() -> { AlertHelper.showAlert(Alert.AlertType.INFORMATION,"Success", (String) event.getNewValue());});
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

    @FXML
    private void handleManageUsers(ActionEvent event) {
        try {

            Parent userView = FXMLLoader.load(getClass().getResource("/gui/UsersManagement.fxml"));
            Scene scene = new Scene(userView);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void handleBackToDashboard(ActionEvent event) {
        try {
            Parent adminView = FXMLLoader.load(getClass().getResource("/gui/AdminView.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(adminView));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}