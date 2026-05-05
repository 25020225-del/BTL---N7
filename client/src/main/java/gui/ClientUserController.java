package gui;

import client.handler.AuctionEventBus;
import client.handler.ClientPaymentHandler;
import client.handler.ResponseDispatcher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import model.auction.Auction;
import model.user.User;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JacksonConfig;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
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
    private static final Logger log = LoggerFactory.getLogger(ClientUserController.class);
    private final ObjectMapper mapper = JacksonConfig.mapper();

    private Parent mainView;
    private Parent createAuctionView;
    private SellerDashboardController sellerController; 
    private Parent tableAuctionView;
    private Parent accountView;
    private Parent settingsView;

    private CreateAuctionController  createAuctionView;


    private User currentUser;

    // FUN
    private int dih = 0;
    private long niggardly = 0;
    private File imagefile;

    @FXML private VBox mainDock;
    @FXML private VBox mainViewController;

    @FXML private HBox searchBarContainer;
    @FXML private TilePane mainTilePane;
    @FXML private TextField searchField;
    @FXML private Button searchButton;

    @FXML
    private TilePane mainTilePane;
    @FXML
    private TextField searchField;
    @FXML
    private Button searchButton;

    @FXML
    private Label accName;
    @FXML
    private Label accUsername;

    private IconButton accountBtn;
    private IconButton toggleList = new IconButton("mdi2m-menu", "List", "List", "special-button");
    private IconButton marketplaceBtn = new IconButton("mdi2s-storefront-outline", "Marketplace", "Marketplace", "special-button");
    private IconButton createAuctionBtn = new IconButton("mdi2a-archive-plus-outline", "Sell Item", "Create Auction", "special-button");
    private IconButton depositBtn = new IconButton("mdi2c-cash-plus", "Deposit 50k (Test)", "Deposit", "special-button");
    private IconButton testCreateAuctionBtn = new IconButton("mdi2b-bug", "Create Bot (Test)", "Test Create", "special-button");
    private IconButton settingsBtn = new IconButton("mdi2c-cog", "Settings", "Settings", "special-button");

    public ClientUserController(User user) throws IOException {
        this.currentUser = user;
        this.accountBtn  = new IconButton("mdi2a-account", "Hello, " + user.getName(), "Account", "special-button");

        FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("MainView.fxml"));
        mainLoader.setController(this);
        mainView = mainLoader.load();

        FXMLLoader sellerLoader = new FXMLLoader(getClass().getResource("CreateAuction.fxml"));
        sellerController = new SellerDashboardController();
        sellerLoader.setController(sellerController);
        createAuctionView = sellerLoader.load();

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

    private void setMainDock() {
        Separator separator = new Separator();
        Region region = new Region();
        VBox.setVgrow(region, Priority.ALWAYS);
        mainDock.getChildren().addAll(
                toggleList,
                marketplaceBtn,
                createAuctionBtn,
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

        depositBtn.setOnAction(event -> requestDeposit(50000));
        testCreateAuctionBtn.setOnAction(event -> UIService.createTestAuction());

            mainViewController.getChildren().clear();

            long currentTime = System.currentTimeMillis();
            if (niggardly == 0 || (currentTime - niggardly > 30000)) {
                dih = 1;
                niggardly = currentTime;
            } else {
                dih++;
            }
            dih = UIService.handleAccountTrollLogic(dih);
            if (dih == 0) niggardly = 0;
=======
>>>>>>> 086780d95c0db8f678d2291acb3205f9a469e85a
        });

        settingsBtn.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(settingsView);
        });
    }

    @FXML
    public void handleBackToMarketplace() {
        mainViewController.getChildren().clear();
        mainViewController.getChildren().add(tableAuctionView);
        MainApplication.networkClient.sendMessage("FETCH_AUCTIONS", "");
    }

    @FXML
    public void handleSignOut() {
        log.info("User \"{}\" is signing out.", currentUser.getName());
        MainApplication.networkClient.sendMessage("LOGOUT", "");
        MainApplication.setNewScene(MainApplication.rootLogin);
    }

    @FXML
    private void handleSelectImage() {
        final int MAX_IMAGE_SIZE = 1024 * 1024;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose an image");
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File selectedFile = fileChooser.showOpenDialog(mainViewController.getScene().getWindow());
        if (selectedFile != null) {
            if (selectedFile.length() > MAX_IMAGE_SIZE) {
                AlertHelper.showAlert(AlertType.ERROR, "Lỗi dung lượng", "Ảnh quá nặng, đề nghị chọn ảnh có dung lượng nhỏ hơn 1MB!");
                return;
            }
            if (sellerController != null) {
                sellerController.setImageFile(selectedFile);
            }
        }
    }

    private void setupSearch() {
        if (searchField == null || mainTilePane == null) return;
        searchField.setOnAction(event -> { if (searchButton != null) searchButton.fire(); });
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

    public void requestDeposit(double amount) {
        if (amount <= 0) {
            AlertHelper.showAlert(AlertType.ERROR, "Error", "Deposit amount must be greater than 0");
            return;
        }
        MainApplication.networkClient.sendMessage("CREATE_DEPOSIT", amount);
    }

    private void openItemDetail(Auction auction) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Productdetail.fxml"));
            Parent detailView = loader.load();

            ItemDetailController detailController = loader.getController();
            detailController.setAuctionData(auction);

            client.handler.ClientAuctionHandler.activeDetailController = detailController;

            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(detailView);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleServerResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            String command = response.getCommand();

            if ("FETCH_AUCTIONS_SUCCESS".equals(command)) {
                try {
                    // MVC Fix: Use Jackson TypeReference for proper Domain Model mapping
                    List<Auction> auctions = mapper.convertValue(
                        response.getData(), 
                        new TypeReference<List<Auction>>() {}
                    );

                    mainTilePane.getChildren().clear();

                    for (Auction auction : auctions) {
                        // Use getters instead of Map.get()
                        String id = auction.getId();
                        String name = auction.getItem().getItemName();
                        String price = String.format("%,.0f", auction.getCurrentPrice());
                        String imageUrl = auction.getItem().getImageUrl();
                        long endTimeMillis = auction.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                        String sellerId = auction.getSeller().getId();

                        MinimalItem item = new MinimalItem(id, imageUrl, name, price, endTimeMillis);
                        item.getAuctionButton().setOnAction(e -> openItemDetail(auction));
                        
                        if (currentUser.getId().equals(sellerId)) {
                            item.addSellerOptions(this::handleEditAuction, this::handleDeleteAuction);
                        }
                        mainTilePane.getChildren().add(item);
                    }
                } catch (Exception e) {
                    log.error("[Client] FETCH_AUCTIONS_SUCCESS parse error: {}", e.getMessage());
                }

            } else if ("NEW_AUCTION_ADDED".equals(command)) {
                try {
                    Auction auction = mapper.convertValue(response.getData(), Auction.class);

                    String id = auction.getId();
                    String name = auction.getItem().getItemName();
                    String price = String.format("%,.0f", auction.getCurrentPrice());
                    String imageUrl = auction.getItem().getImageUrl();
                    long endTimeMillis = auction.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    String sellerId = auction.getSeller().getId();

                    MinimalItem newItem = new MinimalItem(id, imageUrl, name, price, endTimeMillis);
                    newItem.getAuctionButton().setOnAction(e -> openItemDetail(auction));

                    if (currentUser.getId().equals(sellerId)) {
                        newItem.addSellerOptions(this::handleEditAuction, this::handleDeleteAuction);
                    }

                    newItem.setOpacity(0);
                    mainTilePane.getChildren().add(0, newItem);

                    FadeTransition ft = new FadeTransition(Duration.millis(500), newItem);
                    ft.setToValue(1.0);
                    ft.play();
                } catch (Exception e) {
                    log.error("[Client] NEW_AUCTION_ADDED parse error: {}", e.getMessage());
                }

            } else if ("REMOVE_AUCTION".equals(command)) {
                String auctionIdToRemove = (String) response.getData();
                mainTilePane.getChildren().removeIf(node -> auctionIdToRemove.equals(node.getId()));
            } else if ("EDIT_SUCCESS".equals(command) || "DELETE_SUCCESS".equals(command)) {
                AlertHelper.showAlert(AlertType.INFORMATION, "Success", response.getData().toString());
                MainApplication.networkClient.sendMessage("FETCH_AUCTIONS", "");
            } else {
                new ResponseDispatcher().dispatch(response, MainApplication.networkClient);
            }
        });
    }

    private void handleEditAuction(String auctionId) {
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Edit Auction");
        dialog.setHeaderText("Update details for auction: " + auctionId);

        ButtonType saveButtonType = new ButtonType("Save Changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        TextArea descField = new TextArea(); descField.setPrefRowCount(3);
        TextField priceField = new TextField();

        grid.add(new Label("Item Name:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Description:"), 0, 1); grid.add(descField, 1, 1);
        grid.add(new Label("Starting Price:"), 0, 2); grid.add(priceField, 1, 2);

        dialog.getDialogPane().setContent(grid);
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
            if (data.get("itemName").isEmpty() || data.get("startPrice").isEmpty()) {
                AlertHelper.showAlert(AlertType.ERROR, "Validation Error", "Name and Price cannot be empty.");
                return;
            }
            try {
                Double.parseDouble(data.get("startPrice"));
                MainApplication.networkClient.sendMessage("EDIT_AUCTION", data);
            } catch (NumberFormatException e) {
                AlertHelper.showAlert(AlertType.ERROR, "Validation Error", "Invalid price format.");
            }
        });
    }

    private void handleDeleteAuction(String auctionId) {
        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText("Delete Auction?");
        confirm.setContentText("Are you sure you want to delete auction: " + auctionId + "?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                MainApplication.networkClient.sendMessage("DELETE_AUCTION", auctionId);
            }
        });
    }

    public void start() {
        setMainDock();
        setupSearch();
        MainApplication.networkClient.setOnMessageReceived(this::handleServerResponse);
        MainApplication.networkClient.sendMessage("FETCH_AUCTIONS", "");

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
                Alert confirmAlert = new Alert(AlertType.CONFIRMATION);
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
