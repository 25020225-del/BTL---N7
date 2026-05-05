package gui;

import client.handler.AuctionEventBus;
import client.handler.ClientPaymentHandler;
import client.handler.ResponseDispatcher;
import gui.process.*;
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
import model.user.User;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static utils.ConsoleColors.*;

/**
 * The unified primary controller for standard users.
 * This single controller manages both buying (Bidding) and selling (Auction Creation)
 * capabilities, acting as the main dashboard for the application.
 */
public class ClientUserController {

    private Parent mainView;
    private Parent tableAuctionView;
    private Parent accountView;
    private Parent settingsView;

    private CreateAuctionController  createAuctionView;
    private WalletController walletView;


    private User currentUser;

    private File imagefile;

    @FXML private VBox mainDock;
    @FXML private VBox mainViewController;

    @FXML private HBox searchBarContainer;
    @FXML private TilePane mainTilePane;
    @FXML private TextField searchField;
    @FXML private Button searchButton;

    @FXML private Label accName;
    @FXML private Label accUsername;

    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    private IconButton accountBtn;
    private IconButton toggleList           = new IconButton("mdi2m-menu",                  "List",                  "List",            "special-button");
    private IconButton toggleSearchButton   = new IconButton("mdi2f-file-find-outline",     "Search",                "Search",          "special-button");
    private IconButton marketplaceBtn       = new IconButton("mdi2s-storefront-outline",    "Marketplace",           "Marketplace",     "special-button");
    private IconButton createAuctionBtn     = new IconButton("mdi2a-archive-plus-outline",  "Sell Item",             "Create Auction",  "special-button");
    private IconButton walletBtn            = new IconButton("mdi2w-wallet-bifold-outline", "Wallet",                "Wallet",          "special-button");
    private IconButton depositBtn           = new IconButton("mdi2c-cash-plus",             "Deposit 50k (Test)",    "Deposit",         "special-button");
    private IconButton testCreateAuctionBtn = new IconButton("mdi2b-bug",                   "Create Bot (Test)",     "Test Create",     "special-button");
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

        createAuctionView = new CreateAuctionController();
        createAuctionView.setOnAuctionCreated(() -> marketplaceBtn.fire());

        walletView = new WalletController();
        walletView.setOnAuctionCreated(() -> marketplaceBtn.fire());

        FXMLLoader tableViewLoader = new FXMLLoader(getClass().getResource("TableView.fxml"));
        tableViewLoader.setController(this);
        tableAuctionView = tableViewLoader.load();

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

    /**
     * Configures the side navigation dock menu.
     */
    private void setMainDock() {
        Separator separator = new Separator();
        Region region = new Region();
        VBox.setVgrow(region, Priority.ALWAYS);
        mainDock.getChildren().addAll(
                toggleList,
                marketplaceBtn,
                createAuctionBtn,
                walletBtn,
                depositBtn,
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
            mainViewController.getChildren().add(createAuctionView.getParent());
        });

        walletBtn.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(walletView.getParent());
        });

        depositBtn.setOnAction(event -> requestDeposit(50000));

        accountBtn.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(accountView);
        });

        settingsBtn.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(settingsView);
        });
    }

    /**
     * Navigates the user back to the primary marketplace table view.
     */
    @FXML
    public void handleBackToMarketplace() {
        mainViewController.getChildren().clear();
        mainViewController.getChildren().add(tableAuctionView);
        MainApplication.networkClient.sendMessage("FETCH_AUCTIONS", "");
    }

    /**
     * Handles the user sign out process by clearing the session.
     */
    @FXML
    public void handleSignOut() {
        log.info("User \"{}\" is signing out.", currentUser.getName());
        MainApplication.networkClient.sendMessage("LOGOUT", "");
        MainApplication.setNewScene(MainApplication.rootLogin);
    }

    private void setMainViewController() {
        mainTilePane.getChildren().clear();

    }

    /**
     * Initializes the local search functionality within the active marketplace grid.
     */
    private void setupSearch() {
        if (searchField == null || mainTilePane == null) return;

        searchField.setOnAction(event -> {
            if (searchButton != null) searchButton.fire();
        });

        if (searchButton != null) {
            searchButton.setOnAction(event -> {
                String keyword = searchField.getText();
                for (Node node : mainTilePane.getChildren()) {
                    if (node instanceof MinimalItem item) {
                        boolean match = Search.searchText(keyword, item);
                        item.setVisible(match);
                        item.setManaged(match);
                    }
                }
            });
        }
    }

    /**
     * Requests a balance deposit from the server.
     */
    public void requestDeposit(double amount) {
        if (amount <= 0) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Error", "Deposit amount must be greater than 0");
            return;
        }
        MainApplication.networkClient.sendMessage("CREATE_DEPOSIT", amount);
    }
    /**
     * Navigates to the detailed view of a specific auction item.
     */
    private void openItemDetail(Map<String, Object> auctionData) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Productdetail.fxml"));
            Parent detailView = loader.load();

            ItemDetailController detailController = loader.getController();
            detailController.setProductData(auctionData);

            client.handler.ClientAuctionHandler.activeDetailController = detailController;

            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(detailView);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Global router for server messages intended to manipulate the User UI.
     */
    private void handleServerResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            String command = response.getCommand();

            if ("FETCH_AUCTIONS_SUCCESS".equals(command)) {
                try {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> auctions = (List<Map<String, Object>>) response.getData();

                    mainTilePane.getChildren().clear();

                    for (Map<String, Object> data : auctions) {
                        String id      = (String) data.get("id");
                        String name    = (String) data.get("itemName");
                        String price   = String.format("%,.0f", ((Number) data.get("currentPrice")).doubleValue());
                        String imageUrl = (String) data.get("imageUrl");
                        long endTime = ((Number) data.get("endTime")).longValue();
                        String sellerId = (String) data.get("sellerId");

                        MinimalItem item = new MinimalItem(id, imageUrl, name, price, endTime);
                        item.getAuctionButton().setOnAction(e -> openItemDetail(data));
                        
                        // If current user is the seller, add Edit/Delete options
                        if (currentUser.getId().equals(sellerId)) {
                            item.addSellerOptions(this::handleEditAuction, this::handleDeleteAuction);
                        }
                        
                        mainTilePane.getChildren().add(item);
                    }
                } catch (Exception e) {
                    log.error("FETCH_AUCTIONS_SUCCESS parse error: {}", e.getMessage(), e);
                }

            } else if ("NEW_AUCTION_ADDED".equals(command)) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) response.getData();

                    String id = (String) data.get("id");
                    String name = (String) data.get("itemName");
                    String price = String.format("%,.0f", ((Number) data.get("currentPrice")).doubleValue());
                    String imageUrl = (String) data.get("imageUrl");
                    long endTime = ((Number) data.get("endTime")).longValue();
                    String sellerId = (String) data.get("sellerId");

                    MinimalItem newItem = new MinimalItem(id, imageUrl, name, price, endTime);
                    newItem.getAuctionButton().setOnAction(e -> openItemDetail(data));

                    if (currentUser.getId().equals(sellerId)) {
                        newItem.addSellerOptions(this::handleEditAuction, this::handleDeleteAuction);
                    }

                    newItem.setOpacity(0);
                    mainTilePane.getChildren().add(0, newItem);

                    FadeTransition ft = new FadeTransition(Duration.millis(500), newItem);
                    ft.setToValue(1.0);
                    ft.play();
                } catch (Exception e) {}

            } else if ("REMOVE_AUCTION".equals(command)) {
                try {
                    String auctionIdToRemove = (String) response.getData();
                    mainTilePane.getChildren().removeIf(node -> auctionIdToRemove.equals(node.getId()));
                } catch (Exception e) {
                }
            } else if ("EDIT_SUCCESS".equals(command) || "DELETE_SUCCESS".equals(command)) {
                AlertHelper.showAlert(Alert.AlertType.INFORMATION, "Success", response.getData().toString());
                MainApplication.networkClient.sendMessage("FETCH_AUCTIONS", ""); // Refresh list
            } else {
                new ResponseDispatcher().dispatch(response, MainApplication.networkClient);
            }
        });
    }

    private void handleEditAuction(String auctionId) {
        // Create a custom dialog for editing auction details
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Edit Auction");
        dialog.setHeaderText("Update details for auction: " + auctionId);

        // Set the button types
        ButtonType saveButtonType = new ButtonType("Save Changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Create the form grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        nameField.setPromptText("Item Name");
        TextArea descField = new TextArea();
        descField.setPromptText("Description");
        descField.setPrefRowCount(3);
        TextField priceField = new TextField();
        priceField.setPromptText("New Starting Price");

        grid.add(new Label("Item Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Description:"), 0, 1);
        grid.add(descField, 1, 1);
        grid.add(new Label("Starting Price:"), 0, 2);
        grid.add(priceField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        // Convert the result to a map when the save button is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                Map<String, String> result = new HashMap<>();
                result.put("auctionId", auctionId);
                result.put("itemName", nameField.getText());
                result.put("description", descField.getText());
                result.put("startPrice", priceField.getText());
                return result;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(data -> {
            // Basic validation
            if (data.get("itemName").isEmpty() || data.get("startPrice").isEmpty()) {
                AlertHelper.showAlert(AlertType.ERROR, "Validation Error", "Name and Price cannot be empty.");
                return;
            }
            
            try {
                Double.parseDouble(data.get("startPrice"));
            } catch (NumberFormatException e) {
                AlertHelper.showAlert(AlertType.ERROR, "Validation Error", "Invalid price format.");
                return;
            }

            MainApplication.networkClient.sendMessage("EDIT_AUCTION", data);
        });
    }

    private void handleDeleteAuction(String auctionId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText("Delete Auction?");
        confirm.setContentText("Are you sure you want to delete auction: " + auctionId + "?");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                MainApplication.networkClient.sendMessage("DELETE_AUCTION", auctionId);
            }
        });
    }

    /**
     * Bootstraps the controller logic upon initial navigation.
     */
    public void start() {
        setMainDock();
        setMainViewController();
        setupSearch();
        MainApplication.networkClient.setOnMessageReceived(this::handleServerResponse);
        MainApplication.networkClient.sendMessage("FETCH_AUCTIONS", "");

        // Register event bus listeners for decoupled UI notifications
        AuctionEventBus.addListener(AuctionEventBus.AUCTION_CREATED, evt -> {
            Platform.runLater(() -> AlertHelper.showAlert(AlertType.INFORMATION, "Success", evt.getNewValue().toString()));
        });

        AuctionEventBus.addListener(AuctionEventBus.DEPOSIT_SUCCESS, evt -> {
            Platform.runLater(() -> AlertHelper.showAlert(AlertType.INFORMATION, "Deposit Success", evt.getNewValue().toString()));
        });

        AuctionEventBus.addListener(ClientPaymentHandler.PAYMENT_CONFIRM_REQUIRED, evt -> {
            @SuppressWarnings("unchecked")
            Map<String, String> data = (Map<String, String>) evt.getNewValue();
            String orderId = data.get("orderId");

            Platform.runLater(() -> {
                Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                confirmAlert.setTitle("Payment Confirmation");
                confirmAlert.setHeaderText("Have you completed your payment via PayPal?");
                confirmAlert.setContentText("Order ID: " + orderId + "\nClick OK to update your balance.");

                confirmAlert.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        MainApplication.networkClient.sendMessage("CONFIRM_DEPOSIT", orderId);
                    }
                });
            });
        });
    }
}