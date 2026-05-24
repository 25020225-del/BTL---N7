package gui;

import client.handler.AuctionEventBus;
import client.service.AdminService;
import client.service.AuctionService;
import gui.process.AlertUtils;
import gui.process.RemoveEventBus;
import gui.userController.ItemDetailController;
import gui.userController.table.TableControllerAdmin;
import gui.widget.IconButton;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Separator;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.auction.Auction;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * High-privilege workstation workspace supervisor. Coordinates administrative node routing,
 * structural system telemetry listeners configuration, and cross-thread layout synchronization
 * for authoritative platform management.
 */
public class ClientAdminController {
    private static final Logger log = LoggerFactory.getLogger(ClientAdminController.class);

    private final Parent mainView;
    private final SettingsController settingsView;
    private final TableControllerAdmin tableView;
    private final User currentUser;

    @FXML private VBox mainDock;
    @FXML private VBox mainViewController;

    private ItemDetailController currentDetailController;

    private final IconButton account;
    private final IconButton toggleList = new IconButton("mdi2m-menu", "List", "List", "special-button");
    private final IconButton accountList = new IconButton("mdi2a-account-box-multiple-outline", "Accounts", "Manage Accounts", "special-button");
    private final IconButton pendingItemList = new IconButton("mdi2a-archive-settings-outline", "Pending Auction", "Pending Auction", "special-button");
    private final IconButton runningItemList = new IconButton("mdi2a-archive-settings-outline", "Running Auction", "Running Auction", "special-button");
    private final IconButton withdrawList = new IconButton("mdi2c-cash-refund", "Withdrawals", "Withdraw Requests", "special-button");

    /**
     * Mounts the administrative structural composition tree and maps authoritative credentials context.
     *
     * @param user the authenticated administrator account anchor reference
     * @throws IOException if visual asset hierarchy compilation parameters break down
     */
    public ClientAdminController(User user) throws IOException {
        this.currentUser = user;
        this.account = new IconButton("mdi2a-account", "Admin: " + user.getName(), "Account", "special-button");

        FXMLLoader mainViewLoader = new FXMLLoader(getClass().getResource("MainView.fxml"));
        mainViewLoader.setController(this);
        mainView = mainViewLoader.load();

        tableView = new TableControllerAdmin();
        tableView.setOnAuctionListener(this::openItemDetail);

        settingsView = new SettingsController(user);
        settingsView.setOnBackToMarketplace(this::handleBackToMarketplaceInternal);
        settingsView.setOnSignOut(this::handleSignOutInternal);

        MainApplication.setNewScene(mainView);
    }

    private void setMainDock() {
        Region region = new Region();
        Separator separator = new Separator();
        VBox.setVgrow(region, Priority.ALWAYS);
        mainDock.getChildren().addAll(
                toggleList, accountList, pendingItemList, runningItemList, withdrawList,
                separator, region, account
        );

        toggleList.setUserData(true);
        toggleList.setOnAction(event -> {
            boolean collapsed = (boolean) toggleList.getUserData();
            for (Node k : mainDock.getChildren()) {
                if (k instanceof Button b) {
                    b.setContentDisplay(collapsed ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
                }
            }
            toggleList.setUserData(!collapsed);
        });

        pendingItemList.setOnAction(event -> {
            mainViewController.getChildren().setAll(tableView.getParent());
            log.info("Loading pending auctions...");
            AdminService.fetchPendingAuctions();
        });

        runningItemList.setOnAction(event -> {
            mainViewController.getChildren().setAll(tableView.getParent());
            log.info("Loading running auctions...");
            AdminService.fetchRunningAuctions();
        });

        accountList.setOnAction(event -> {
            mainViewController.getChildren().setAll(tableView.getParent());
            log.info("Loading user list...");
            AdminService.fetchUsers();
        });

        account.setOnAction(event -> mainViewController.getChildren().setAll(settingsView));

        withdrawList.setOnAction(event -> {
            mainViewController.getChildren().setAll(tableView.getParent());
            log.info("Loading withdraw requests...");
            AdminService.fetchWithdrawRequests();
        });
    }

    private void openItemDetail(Auction auction) {
        if (currentDetailController != null) {
            currentDetailController.dispose();
        }

        ItemDetailController detailController = new ItemDetailController(currentUser);
        detailController.setAuctionData(auction);
        currentDetailController = detailController;
        AuctionService.fetchTransactions(auction.getId());
        detailController.setOnReturnToMarketplace(this::handleBackToMarketplaceInternal);

        mainViewController.getChildren().clear();
        mainViewController.getChildren().add(detailController.getParent());
    }

    private void handleBackToMarketplaceInternal() {
        pendingItemList.fire();
    }

    private void handleSignOutInternal() {
        RemoveEventBus.forUser();
        RemoveEventBus.forAdmin();
        log.info("Admin is signing out.");
        AdminService.logout();
        MainApplication.setNewScene(MainApplication.rootLogin);
    }

    @FXML
    private void handleBackToMarketplace() {
        handleBackToMarketplaceInternal();
    }

    @FXML
    private void handleSignOut() {
        handleSignOutInternal();
    }

    private void setMainViewController() {
        AuctionEventBus.addListener("ADMIN_ACTION_SUCCESS", event ->
                Platform.runLater(() -> AlertUtils.showInfo("Success", (String) event.getNewValue()))
        );
        AuctionEventBus.addListener(AuctionEventBus.ADMIN_ACTION_SUCCESS, event ->
                Platform.runLater(() -> {
                    AlertUtils.showInfo("Success", event.getNewValue().toString());
                    AuctionService.fetchAuctions();
                })
        );
        AuctionEventBus.addListener(AuctionEventBus.GENERAL_ERROR, event ->
                Platform.runLater(() -> AlertUtils.showError("Error", event.getNewValue().toString()))
        );
    }

    /**
     * Activates the component network registration frames and aggregates the layout view models.
     */
    public void start() {
        setMainDock();
        setMainViewController();
        log.info("Admin view initialized successfully.");
    }
}