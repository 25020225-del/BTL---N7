package gui;

import client.handler.AuctionEventBus;
import client.handler.ClientPaymentHandler;
import client.service.AuctionService;
import client.service.UserService;
import client.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import gui.process.AlertUtils;
import gui.process.RemoveEventBus;
import gui.userController.CreateAuctionController;
import gui.userController.ItemDetailController;
import gui.userController.WalletController;
import gui.userController.table.TableControllerUser;
import gui.widget.IconButton;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import model.auction.Auction;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JacksonConfig;

import java.beans.PropertyChangeListener;
import java.io.IOException;

import static client.handler.ClientPaymentHandler.PAYMENT_CONFIRM_REQUIRED;

/**
 * Core customer portal dashboard workspace orchestrator. Implements full-duplex workspace navigation loops,
 * maps structural presentation lifecycle models, and isolates standard commerce interfaces
 * including marketplace catalogs, asset creators, and financial ledgers.
 */
public class ClientUserController {
    private static final Logger log = LoggerFactory.getLogger(ClientUserController.class);
    private final ObjectMapper mapper = JacksonConfig.mapper();

    private final Parent mainView;
    private final CreateAuctionController createAuctionController;
    private final Parent accountView;
    private final Parent settingsView;
    private final WalletController walletView;
    private final TableControllerUser tableView;
    private final User currentUser;

    private PropertyChangeListener errorListener;
    private PropertyChangeListener auctionCreatedListener;
    private PropertyChangeListener depositListener;
    private PropertyChangeListener paymentListener;

    @FXML private VBox mainDock;
    @FXML private VBox mainViewController;
    @FXML private Label accName;
    @FXML private Label accUsername;

    private final IconButton accountBtn;
    private final IconButton toggleList = new IconButton("mdi2m-menu", "List", "List", "special-button");
    private final IconButton marketplaceBtn = new IconButton("mdi2s-storefront-outline", "Marketplace", "Marketplace", "special-button");
    private final IconButton myAuctionsBtn = new IconButton("mdi2s-storefront-outline", "My Auctions", "My Auctions", "special-button");
    private final IconButton createAuctionBtn = new IconButton("mdi2a-archive-plus-outline", "Sell Item", "Create Auction", "special-button");
    private final IconButton walletBtn = new IconButton("mdi2w-wallet-bifold-outline", "Wallet", "Wallet", "special-button");
    private final IconButton settingsBtn = new IconButton("mdi2c-cog", "Settings", "Settings", "special-button");

    private ItemDetailController currentDetailController;

    /**
     * Initializes structural composite workspaces and registers structural view model hierarchies.
     *
     * @param user the validated system interactive commerce profile actor reference
     * @throws IOException if local file parsing routes break down on layout loading
     */
    public ClientUserController(User user) throws IOException {
        this.currentUser = user;
        this.accountBtn = new IconButton("mdi2a-account", "Hello, " + user.getName(), "Account", "special-button");

        FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("MainView.fxml"));
        mainLoader.setController(this);
        mainView = mainLoader.load();

        createAuctionController = new CreateAuctionController();
        walletView = new WalletController();
        walletView.setOnReturnAction(() -> marketplaceBtn.fire());

        tableView = new TableControllerUser();
        tableView.setOnAuctionListener(this::openItemDetail);

        FXMLLoader accountLoader = new FXMLLoader(getClass().getResource("AccountView.fxml"));
        accountLoader.setController(this);
        accountView = accountLoader.load();

        accName.setText("Full Name: " + currentUser.getName());
        accUsername.setText("Username: " + currentUser.getUserName());

        SettingsController settingsCtrl = new SettingsController(currentUser);
        settingsCtrl.setOnBackToMarketplace(this::handleBackToMarketplace);
        settingsCtrl.setOnSignOut(this::handleSignOut);
        settingsView = settingsCtrl;

        MainApplication.setNewScene(mainView);
    }

    private void setMainDock() {
        Separator separator = new Separator();
        Region region = new Region();
        VBox.setVgrow(region, Priority.ALWAYS);
        mainDock.getChildren().addAll(
                toggleList, marketplaceBtn, myAuctionsBtn, createAuctionBtn, walletBtn,
                separator, region, settingsBtn, accountBtn
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

        marketplaceBtn.setOnAction(event -> handleBackToMarketplace());

        myAuctionsBtn.setOnAction(event -> {
            if (currentDetailController != null) {
                currentDetailController.dispose();
                currentDetailController = null;
            }
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(tableView.getParent());
            AuctionService.fetchMyAuctions();
        });

        createAuctionBtn.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(createAuctionController);
        });

        walletBtn.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(walletView);
            WalletService.fetchWalletHistory();
        });

        settingsBtn.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(settingsView);
            ((SettingsController) settingsView).initialize();
        });
    }

    /**
     * Dismisses deep view nodes and routes screen graph containers back onto the primary marketplace view.
     */
    @FXML
    public void handleBackToMarketplace() {
        if (currentDetailController != null) {
            currentDetailController.dispose();
            currentDetailController = null;
        }
        mainViewController.getChildren().clear();
        mainViewController.getChildren().add(tableView.getParent());
        AuctionService.fetchAuctions();
    }

    /**
     * Invalidates local telemetry caches, executes remote session tear down commands,
     * and returns app state back onto the login display framework.
     */
    @FXML
    public void handleSignOut() {
        unregisterListeners();
        log.info("User \"{}\" is signing out.", currentUser.getName());
        UserService.logout();
        RemoveEventBus.forUser();
        currentDetailController = null;
        MainApplication.setNewScene(MainApplication.rootLogin);
    }

    private void openItemDetail(Auction auction) {
        if (currentDetailController != null) {
            currentDetailController.dispose();
        }

        ItemDetailController detailController = new ItemDetailController(currentUser);
        detailController.setAuctionData(auction);
        currentDetailController = detailController;
        AuctionService.fetchTransactions(auction.getId());
        detailController.setOnReturnToMarketplace(() -> marketplaceBtn.fire());

        mainViewController.getChildren().clear();
        mainViewController.getChildren().add(detailController.getParent());
    }

    /**
     * Binds general system aggregate event interceptors and hooks runtime polling commands.
     */
    public void start() {
        setMainDock();
        AuctionService.fetchAuctions();
        registerListeners();
    }

    private void registerListeners() {
        // Xóa cũ trước khi đăng ký mới (phòng trường hợp start() gọi lại)
        unregisterListeners();

        auctionCreatedListener = evt ->
                Platform.runLater(() -> AlertUtils.showInfo("Success", evt.getNewValue().toString()));

        depositListener = evt ->
                Platform.runLater(() -> AlertUtils.showInfo("Deposit Success", evt.getNewValue().toString()));

        errorListener = evt -> {
            String msg = evt.getNewValue() != null ? evt.getNewValue().toString() : "Unknown error";
            Platform.runLater(() -> AlertUtils.showError("System Error", msg));
        };
        paymentListener = evt ->
                Platform.runLater(() -> AlertUtils.showInfo("Payment in process", "Payment gate has been opened."));

        AuctionEventBus.addListener(AuctionEventBus.AUCTION_CREATED,   auctionCreatedListener);
        AuctionEventBus.addListener(AuctionEventBus.DEPOSIT_SUCCESS,    depositListener);
        AuctionEventBus.addListener(AuctionEventBus.GENERAL_ERROR,      errorListener);
        AuctionEventBus.addListener(PAYMENT_CONFIRM_REQUIRED,           paymentListener);
    }
    public void unregisterListeners() {
        AuctionEventBus.removeListener(AuctionEventBus.AUCTION_CREATED,  auctionCreatedListener);
        AuctionEventBus.removeListener(AuctionEventBus.DEPOSIT_SUCCESS,   depositListener);
        AuctionEventBus.removeListener(AuctionEventBus.GENERAL_ERROR,     errorListener);
        AuctionEventBus.removeListener(PAYMENT_CONFIRM_REQUIRED,          paymentListener);
    }

}