package gui;

import client.handler.AuctionEventBus;
import client.handler.ClientPaymentHandler;
import client.service.AuctionService;
import client.service.UserService;
import client.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import gui.process.*;
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
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.*;
import model.auction.Auction;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JacksonConfig;

import java.io.IOException;

/**
 * The unified primary controller for standard users.
 * This single controller manages both buying (Bidding) and selling (Auction Creation)
 * capabilities, acting as the main dashboard for the application.
 */
public class ClientUserController {
    private static final Logger log = LoggerFactory.getLogger(ClientUserController.class);
    private final ObjectMapper mapper = JacksonConfig.mapper();

    private Parent mainView;
    private CreateAuctionController createAuctionController;
    private ItemDetailController currentDetailController;
    private Parent accountView;
    private Parent settingsView;

    private WalletController walletView;
    private TableControllerUser tableView;

    private User currentUser;

    @FXML private VBox mainDock;
    @FXML private VBox mainViewController;


    @FXML private Label accName;
    @FXML private Label accUsername;

    private IconButton accountBtn;
    private IconButton toggleList           = new IconButton("mdi2m-menu",                  "List",                  "List",            "special-button");
    private IconButton marketplaceBtn       = new IconButton("mdi2s-storefront-outline",    "Marketplace",           "Marketplace",     "special-button");
    private IconButton myAuctionsBtn       = new IconButton("mdi2s-storefront-outline",     "My Auctions",           "My Auctions",     "special-button");
    private IconButton createAuctionBtn     = new IconButton("mdi2a-archive-plus-outline",  "Sell Item",             "Create Auction",  "special-button");
    private IconButton walletBtn            = new IconButton("mdi2w-wallet-bifold-outline", "Wallet",                "Wallet",          "special-button");
    private IconButton settingsBtn          = new IconButton("mdi2c-cog",                   "Settings",              "Settings",        "special-button");

    /**
     * Initializes the Unified User Controller and loads all required FXML layouts.
     *
     * @param user The currently authenticated user instance.
     * @throws IOException If FXML files cannot be loaded.
     */
    public ClientUserController(User user) throws IOException {
        this.currentUser = user;
        this.accountBtn  = new IconButton("mdi2a-account", "Hello, " + user.getName(), "Account", "special-button");

        FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("MainView.fxml"));
        mainLoader.setController(this);
        mainView = mainLoader.load();

        createAuctionController = new CreateAuctionController();

        // [KIẾN TRÚC MỚI] Khởi tạo Custom Control WalletController
        walletView = new WalletController();
        // [FIX] Cập nhật tên hàm thành setOnReturnAction cho đúng chuẩn bên WalletController
        walletView.setOnReturnAction(() -> marketplaceBtn.fire());

        tableView = new TableControllerUser();
        tableView.setOnAuctionListener((auction) -> openItemDetail(auction));

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
                toggleList,
                marketplaceBtn,
                myAuctionsBtn,
                createAuctionBtn,
                walletBtn,
                separator,
                region,
                settingsBtn,
                accountBtn
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

        // [FIX TRỌNG TÂM] Vì walletView giờ đã extends VBox nên nó CHÍNH LÀ một Node.
        // Chỉ cần add thẳng walletView vào mainViewController, không gọi .getParent() nữa.
        walletBtn.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(walletView);
            WalletService.fetchWalletHistory();
        });

        settingsBtn.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(settingsView);
            ((SettingsController) settingsView)
                    .initialize();
        });
    }

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


    @FXML
    public void handleSignOut() {
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
        detailController.setOnReturnToMarketplace(() -> {
            marketplaceBtn.fire();
        });
        mainViewController.getChildren().clear();
        mainViewController.getChildren().add(detailController.getParent());
    }

    public void start() {
        setMainDock();
        AuctionService.fetchAuctions();

        AuctionEventBus.addListener(AuctionEventBus.AUCTION_CREATED, evt -> {
            Platform.runLater(() -> AlertHelper.showAlert(AlertType.INFORMATION, "Success", evt.getNewValue().toString()));
        });

        AuctionEventBus.addListener(AuctionEventBus.DEPOSIT_SUCCESS, evt -> {
            Platform.runLater(() -> AlertHelper.showAlert(AlertType.INFORMATION, "Deposit Success", evt.getNewValue().toString()));
        });

        AuctionEventBus.addListener(AuctionEventBus.GENERAL_ERROR, evt -> {
            String msg = evt.getNewValue() != null ? evt.getNewValue().toString() : "An unknown error occurred.";
            Platform.runLater(() -> AlertHelper.showAlert(AlertType.ERROR, "System Error", msg));
        });

        AuctionEventBus.addListener(ClientPaymentHandler.PAYMENT_CONFIRM_REQUIRED, evt -> {
            Platform.runLater(() -> {
                AlertHelper.showAlert(AlertType.INFORMATION, "Payment in process",
                        "Payment gate has been opened.");
            });
        });
    }
}