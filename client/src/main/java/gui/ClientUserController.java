package gui;

import client.handler.ResponseDispatcher;
import gui.process.AlertHelper;
import gui.process.CropImage;
import gui.process.ImageCompressor;
import gui.process.Search;
import gui.widget.IconButton;
import gui.widget.MinimalItem;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import model.auction.Auction;
import model.item.Item;
import model.user.User;
import network.NetworkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The unified primary controller for standard users.
 * This single controller manages both buying (Bidding) and selling (Auction Creation)
 * capabilities, acting as the main dashboard for the application.
 */
public class ClientUserController {
    private static final Logger log = LoggerFactory.getLogger(ClientUserController.class);

    private Parent mainView;
    private Parent createAuctionView;
    private SellerDashboardController sellerController; // Module nhỏ xử lý nghiệp vụ Seller
    private Parent tableAuctionView;
    private Parent accountView;
    private Parent settingsView;

    private User currentUser;

    private File imagefile;

    // FUN
    private int dih = 0;
    private long niggardly = 0;

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

    @FXML
    private TextField ca_itemName;
    @FXML
    private TextArea ca_description;
    @FXML
    private TextField ca_startPrice;
    @FXML
    private TextField ca_bidIncrement;
    @FXML
    private DatePicker ca_startDate;
    @FXML
    private TextField ca_startHour;
    @FXML
    private TextField ca_startMinute;
    @FXML
    private TextField ca_durationDays;
    @FXML
    private TextField ca_durationHours;
    @FXML
    private ImageView ca_image;

    @FXML
    private Label accName;
    @FXML
    private Label accUsername;

    private IconButton accountBtn;
    private IconButton toggleList = new IconButton("mdi2m-menu", "List", "List", "special-button");
    private IconButton toggleSearchButton = new IconButton("mdi2f-file-find-outline", "Search", "Search", "special-button");
    private IconButton marketplaceBtn = new IconButton("mdi2s-storefront-outline", "Marketplace", "Marketplace", "special-button");
    private IconButton createAuctionBtn = new IconButton("mdi2a-archive-plus-outline", "Sell Item", "Create Auction", "special-button");
    private IconButton depositBtn = new IconButton("mdi2c-cash-plus", "Deposit 50k (Test)", "Deposit", "special-button");
    private IconButton testCreateAuctionBtn = new IconButton("mdi2b-bug", "Create Bot (Test)", "Test Create", "special-button");
    private IconButton settingsBtn = new IconButton("mdi2c-cog", "Settings", "Settings", "special-button");

    /**
     * Initializes the Unified User Controller and loads all required FXML layouts.
     *
     * @param user The currently authenticated user instance.
     * @throws IOException If FXML files cannot be loaded.
     */
    public ClientUserController(User user) throws IOException {
        this.currentUser = user;
        this.accountBtn = new IconButton("mdi2a-account", "Hello, " + user.getName(), "Account", "special-button");

        FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("MainView.fxml"));
        mainLoader.setController(this);
        mainView = mainLoader.load();

        // Tách biệt logic Seller
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
                depositBtn,
                testCreateAuctionBtn,
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
            mainViewController.getChildren().add(createAuctionView);
        });

        depositBtn.setOnAction(event -> requestDeposit(50000));

        testCreateAuctionBtn.setOnAction(event -> {
            String testItemName = "TEST_ITEM_" + (System.currentTimeMillis() % 10000);
            Map<String, String> dummyData = new HashMap<>();
            dummyData.put("itemName", testItemName);
            dummyData.put("description", "Test description");
            dummyData.put("startingPrice", "50000");
            dummyData.put("bidIncrement", "5000");
            dummyData.put("durationMinutes", "60");

            log.debug("Sending CREATE_AUCTION for {}", testItemName);
            MainApplication.networkClient.sendMessage("CREATE_AUCTION", dummyData);
        });

        accountBtn.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(accountView);

            // FUN
            long skibidi_toilet = System.currentTimeMillis();
            if (niggardly == 0 || (skibidi_toilet - niggardly > 30000)) {
                dih = 1;
                niggardly = skibidi_toilet;
            } else dih++;

            if (dih == 67) {
                try {
                    String troll = "https://www.tiktok.com/@rven6166/video/7615455293991963934";

                    if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI(troll));
                    }
                } catch (Exception e) {
                    log.error("{}", e.getMessage());
                }
                dih = 0;
                niggardly = 0;
            }
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
        log.info("\"{}\" is signing out.", currentUser.getUserName());
        MainApplication.networkClient.sendMessage("LOGOUT", "");
        MainApplication.setNewScene(MainApplication.rootLogin);
    }

    private void setMainViewController() {
        mainTilePane.getChildren().clear();

    }

    /**
     * Choose image from local storage.
     */
    @FXML
    private void handleSelectImage() {
        // Hạ giới hạn xuống 1MB để bảo vệ RAM của Server khi mã hóa và parse JSON
        final int MAX_IMAGE_SIZE = 1 * 1024 * 1024;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose an image");
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File selectedFile = fileChooser.showOpenDialog(mainViewController.getScene().getWindow());

        if (selectedFile != null) {
            if (selectedFile.length() > MAX_IMAGE_SIZE) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, "Lỗi dung lượng", "Ảnh quá nặng, đề nghị chọn ảnh có dung lượng nhỏ hơn 1MB!");
                return;
            }

            // Đồng bộ với SellerController
            if (sellerController != null) {
                sellerController.setImageFile(selectedFile);
            }
            log.info("Selected file: {}", selectedFile.getName());
        }
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
                        String id = (String) data.get("id");
                        String name = (String) data.get("itemName");
                        String price = String.format("%,.0f", ((Number) data.get("currentPrice")).doubleValue());
                        String imageUrl = (String) data.get("imageUrl");
                        long endTime = ((Number) data.get("endTime")).longValue();

                        MinimalItem item = new MinimalItem(id, imageUrl, name, price, endTime);
                        item.getAuctionButton().setOnAction(e -> openItemDetail(data));
                        mainTilePane.getChildren().add(item);
                    }
                } catch (Exception e) {
                    System.out.println("[ClientAuctionHandler]: FETCH_AUCTIONS_SUCCESS parse error: " + e.getMessage());
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

                    MinimalItem newItem = new MinimalItem(id, imageUrl, name, price, endTime);
                    newItem.getAuctionButton().setOnAction(e -> openItemDetail(data));

                    newItem.setOpacity(0);
                    mainTilePane.getChildren().add(0, newItem);

                    FadeTransition ft = new FadeTransition(Duration.millis(500), newItem);
                    ft.setToValue(1.0);
                    ft.play();
                } catch (Exception e) {
                }

            } else if ("REMOVE_AUCTION".equals(command)) {
                try {
                    String auctionIdToRemove = (String) response.getData();
                    mainTilePane.getChildren().removeIf(node -> auctionIdToRemove.equals(node.getId()));
                } catch (Exception e) {
                }

            } else {
                new ResponseDispatcher().dispatch(response, MainApplication.networkClient);
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
    }
}