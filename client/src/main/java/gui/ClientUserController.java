package gui;

import client.handler.AuctionEventBus;
import client.handler.ClientPaymentHandler;
import client.handler.ResponseDispatcher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.tools.javac.Main;
import gui.process.*;
import gui.userController.CreateAuctionController;
import gui.userController.ItemDetailController;
import gui.userController.TableController;
import gui.userController.WalletController;
import gui.widget.IconButton;
import gui.widget.MinimalItem;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.*;
import javafx.util.Duration;
import model.auction.Auction;
import model.user.User;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JacksonConfig;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

    private CreateAuctionController createAuctionView; // Có thể thừa, nhưng anh giữ nguyên cấu trúc của em
    private WalletController walletView;
    private TableController tableView;

    private User currentUser;

    @FXML private VBox mainDock;
    @FXML private VBox mainViewController;


    @FXML private Label accName;
    @FXML private Label accUsername;

    private IconButton accountBtn;
    private IconButton toggleList           = new IconButton("mdi2m-menu",                  "List",                  "List",            "special-button");
    private IconButton marketplaceBtn       = new IconButton("mdi2s-storefront-outline",    "Marketplace",           "Marketplace",     "special-button");
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

        tableView = new TableController();
        tableView.setOnAuctionListener((auction) -> openItemDetail(auction));

        FXMLLoader accountLoader = new FXMLLoader(getClass().getResource("AccountView.fxml"));
        accountLoader.setController(this);
        accountView = accountLoader.load();

        accName.setText("Full Name: " + currentUser.getName());
        accUsername.setText("Username: " + currentUser.getUserName());

        FXMLLoader settingsLoader = new FXMLLoader(getClass().getResource("SettingsView.fxml"));
        settingsLoader.setController(this);
        settingsView = settingsLoader.load();

        MainApplication.setNewScene(mainView);
    }

    private void setMainDock() {
        Separator separator = new Separator();
        Region region = new Region();
        VBox.setVgrow(region, Priority.ALWAYS);
        mainDock.getChildren().addAll(
                toggleList,
                marketplaceBtn,
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

        createAuctionBtn.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(createAuctionController);
        });

        // [FIX TRỌNG TÂM] Vì walletView giờ đã extends VBox nên nó CHÍNH LÀ một Node.
        // Chỉ cần add thẳng walletView vào mainViewController, không gọi .getParent() nữa.
        walletBtn.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(walletView);
        });

        settingsBtn.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(settingsView);
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
        MainApplication.networkClient.sendMessage("FETCH_AUCTIONS", "");
    }

    @FXML
    public void handleSignOut() {
        log.info("User \"{}\" is signing out.", currentUser.getName());
        MainApplication.networkClient.sendMessage("LOGOUT", "");
        AuctionEventBus.removeAllListeners(AuctionEventBus.AUCTION_CREATED);
        AuctionEventBus.removeAllListeners(AuctionEventBus.DEPOSIT_SUCCESS);
        AuctionEventBus.removeAllListeners(AuctionEventBus.GENERAL_ERROR);
        AuctionEventBus.removeAllListeners(ClientPaymentHandler.PAYMENT_CONFIRM_REQUIRED);
        createAuctionView = null;
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
        MainApplication.networkClient.sendMessage("FETCH_TRANSACTIONS",auction.getId());

        mainViewController.getChildren().clear();
        mainViewController.getChildren().add(detailController.getParent());
    }

    private void handleServerResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            String command = response.getCommand();

            switch (command) {
                case "FETCH_AUCTIONS_SUCCESS" -> {
                    try {
                        List<Map<String, Object>> auctions = mapper.convertValue(
                                response.getData(),
                                new TypeReference<List<Map<String, Object>>>() {}
                        );
                        tableView.addAllAuction(auctions);
                    } catch (Exception e) {
                        log.error("[Client] FETCH_AUCTIONS_SUCCESS parse error: {}", e.getMessage());
                    }
                }
                case "FETCH_TRANSACTION_SUCCESS" -> {
                    try {
                        List<Map<String,Object>> transHistory = mapper.convertValue(
                                response.getData(),
                                new TypeReference<List<Map<String,Object>>>() {}
                        );
                        currentDetailController.setTransActionHistoryData(transHistory);
                    } catch (Exception e) {
                        log.error("[Client] FETCH_TRANSACTION_SUCCESS parse error: {}", e.getMessage());
                    }
                }
                case "NEW_AUCTION_ADDED" -> {
                    try {
                        Map<String, Object> auction = mapper.convertValue(
                                response.getData(),
                                new TypeReference<Map<String, Object>>() {}
                        );

                        tableView.addNewAuction(auction);
                    } catch (Exception e) {
                        log.error("[Client] NEW_AUCTION_ADDED parse error: {}", e.getMessage());
                    }
                }
                case "REMOVE_AUCTION" -> {
                    String auctionIdToRemove = (String) response.getData();
                    tableView.removeAuction(auctionIdToRemove);
                }
                case "EDIT_SUCCESS", "DELETE_SUCCESS" -> {
                    AlertHelper.showAlert(AlertType.INFORMATION, "Success", response.getData().toString());
                    MainApplication.networkClient.sendMessage("FETCH_AUCTIONS", "");
                }
                case null, default -> new ResponseDispatcher().dispatch(response, MainApplication.networkClient);
            }
        });
    }

    public void start() {
        setMainDock();
        MainApplication.networkClient.setOnMessageReceived(this::handleServerResponse);
        MainApplication.networkClient.sendMessage("FETCH_AUCTIONS", "");

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