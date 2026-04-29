package gui;

import client.handler.ResponseDispatcher;
import gui.process.AlertHelper;
import gui.process.AnimateEffect;
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
import javafx.scene.layout.*;
import javafx.util.Duration;
import model.User;
import network.NetworkMessage;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static utils.ConsoleColors.*;

public class ClientUserController {

    private Parent mainView;
    private Parent createAuctionView;
    private Parent tableAuctionView;

    private User currentUser;

    @FXML private VBox mainDock;
    @FXML private VBox mainViewController;

    @FXML private HBox searchBarContainer;
    @FXML private TilePane mainTilePane;
    @FXML private TextField searchField;
    @FXML private Button searchButton;

    @FXML private TextField ca_itemName;
    @FXML private TextArea ca_description;
    @FXML private TextField ca_startPrice;
    @FXML private TextField ca_bidIncrement;
    @FXML private DatePicker ca_startDate;
    @FXML private DatePicker ca_endDate;
    @FXML private TextField ca_startHour;
    @FXML private TextField ca_startMinute;
    @FXML private TextField ca_endHour;
    @FXML private TextField ca_endMinute;


    private IconButton accountBtn;
    private IconButton toggleList    = new IconButton("mdi2m-menu",                  "List",                  "List",            "special-button");
    private IconButton toggleSearchButton = new IconButton("mdi2f-file-find-outline","Search",                "Search",          "special-button");
    private IconButton marketplaceBtn = new IconButton("mdi2s-storefront-outline",   "Chợ đấu giá",           "Marketplace",     "special-button");
    private IconButton createAuctionBtn = new IconButton("mdi2a-archive-plus-outline","Đăng bán",             "Create Auction",  "special-button");
    private IconButton depositBtn     = new IconButton("mdi2c-cash-plus",            "Deposit 50,000 (Test)", "Deposit",         "special-button");
    private IconButton testCreateAuctionBtn = new IconButton("mdi2b-bug",            "Create Auction (Test)", "Test Create",     "special-button");

    public ClientUserController(User user) throws IOException {
        this.currentUser = user;
        this.accountBtn  = new IconButton("mdi2a-account", "Hello, " + user.getName(), "Account", "special-button");

        FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("MainView.fxml"));
        mainLoader.setController(this);
        mainView = mainLoader.load();

        FXMLLoader sellerLoader = new FXMLLoader(getClass().getResource("CreateAuction.fxml"));
        sellerLoader.setController(this);
        createAuctionView = sellerLoader.load();


        FXMLLoader tableViewLoader = new FXMLLoader(getClass().getResource("TableView.fxml"));
        tableViewLoader.setController(this);
        tableAuctionView = tableViewLoader.load();

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
                testCreateAuctionBtn,
                separator,
                region,
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


        // Navigate to marketplace and refresh listings
        marketplaceBtn.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(tableAuctionView);
            MainApplication.networkClient.sendMessage("FETCH_AUCTIONS", "");
        });

        // Navigate to create-auction form
        createAuctionBtn.setOnAction(event -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(createAuctionView);
        });

        depositBtn.setOnAction(event -> requestDeposit(50000));

        testCreateAuctionBtn.setOnAction(event -> {
            String testItemName = "TEST_ITEM_" + (System.currentTimeMillis() % 10000);
            Map<String, String> dummyData = new HashMap<>();
            dummyData.put("itemName",       testItemName);
            dummyData.put("description",    "Test description");
            dummyData.put("startingPrice",  "50000");
            dummyData.put("bidIncrement",   "5000");
            dummyData.put("durationMinutes","60");

            System.out.println("[Debug]: Sending CREATE_AUCTION for " + testItemName);
            MainApplication.networkClient.sendMessage("CREATE_AUCTION", dummyData);
        });
    }

    private void setMainViewController() {
        mainTilePane.getChildren().clear();
    }

    private void setupSearch() {
        if (searchField == null || mainTilePane == null) {
            System.out.println("[Warning]: " + YELLOW + "searchField or mainTilePane not found – check @FXML bindings." + RESET);
            return;
        }

        searchField.setOnAction(event -> {
            if (searchButton != null) searchButton.fire();
        });

        if (searchButton != null) {
            searchButton.setOnAction(event -> {
                String keyword = searchField.getText();
                System.out.println("[Log]: Searching for: " + YELLOW + keyword + RESET);
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
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Error", "Deposit amount must be greater than 0");
            return;
        }
        System.out.println("[Log]: Sending deposit request of " + amount + " VND...");
        MainApplication.networkClient.sendMessage("CREATE_DEPOSIT", amount);
    }

    @FXML
    public void handleSubmitAuction(javafx.event.ActionEvent event) {
        System.out.println("[Log]: Processing create auction request...");
        try {
            TextField  inputName       = ca_itemName;
            TextArea   inputDesc       = ca_description;
            TextField  inputStartPrice = ca_startPrice;
            TextField  inputBidInc     = ca_bidIncrement;
            DatePicker startDate       = ca_startDate;
            DatePicker endDate         = ca_endDate;
            TextField  startHour       = ca_startHour;
            TextField  startMinute     = ca_startMinute;
            TextField  endHour         = ca_endHour;
            TextField  endMinute       = ca_endMinute;

            // Validate text fields
            String name       = inputName.getText().trim();
            String desc       = inputDesc.getText().trim();
            String startPrice = inputStartPrice.getText().trim();
            String bidInc     = inputBidInc.getText().trim();

            if (name.isEmpty() || desc.isEmpty() || startPrice.isEmpty() || bidInc.isEmpty()) {
                AlertHelper.showAlert(Alert.AlertType.WARNING, "Missing Fields", "Please fill in all required fields");
                return;
            }

            // Validate dates
            if (startDate.getValue() == null || endDate.getValue() == null) {
                AlertHelper.showAlert(Alert.AlertType.WARNING, "Missing Date", "Please select both start and end dates");
                return;
            }

            // Parse and validate time fields
            LocalDateTime startDT = LocalDateTime.of(
                    startDate.getValue(),
                    LocalTime.of(Integer.parseInt(startHour.getText().trim()),
                            Integer.parseInt(startMinute.getText().trim()))
            );
            LocalDateTime endDT = LocalDateTime.of(
                    endDate.getValue(),
                    LocalTime.of(Integer.parseInt(endHour.getText().trim()),
                            Integer.parseInt(endMinute.getText().trim()))
            );

            long durationMinutes = java.time.Duration.between(startDT, endDT).toMinutes();
            if (durationMinutes <= 0) {
                AlertHelper.showAlert(Alert.AlertType.WARNING, "Invalid Time", "End time must be after start time");
                return;
            }

            // Validate numeric fields
            Double.parseDouble(startPrice);
            Double.parseDouble(bidInc);

            // Build payload and send
            Map<String, String> auctionData = new HashMap<>();
            auctionData.put("itemName",        name);
            auctionData.put("description",     desc);
            auctionData.put("startingPrice",   startPrice);
            auctionData.put("bidIncrement",    bidInc);
            auctionData.put("durationMinutes", String.valueOf(durationMinutes));

            System.out.println("[Log]: Sending CREATE_AUCTION for " + YELLOW + name + RESET + "...");
            MainApplication.networkClient.sendMessage("CREATE_AUCTION", auctionData);

            // Clear form
            inputName.clear();       inputDesc.clear();
            inputStartPrice.clear(); inputBidInc.clear();
            startHour.clear();       startMinute.clear();
            endHour.clear();         endMinute.clear();
            startDate.setValue(null); endDate.setValue(null);

            // Navigate back to marketplace
            if (marketplaceBtn != null) marketplaceBtn.fire();

        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Format Error", "Price, hours, and minutes must be valid numbers");
        } catch (java.time.DateTimeException e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Time Error", "Hour must be 0–23 and minute must be 0–59");
        } catch (Exception e) {
            System.out.println("[Error]: " + RED + e.getMessage() + RESET);
            e.printStackTrace();
        }
    }

    private void handleServerResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            String command = response.getCommand();

            if ("FETCH_AUCTIONS_SUCCESS".equals(command)) {
                try {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> auctions =
                            (List<Map<String, Object>>) response.getData();

                    mainTilePane.getChildren().clear();

                    for (Map<String, Object> data : auctions) {
                        String name    = (String) data.get("itemName");
                        String price   = String.format("%,.0f", ((Number) data.get("currentPrice")).doubleValue());
                        long   endTime = ((Number) data.get("endTime")).longValue();

                        mainTilePane.getChildren().add(new MinimalItem(name, price, endTime));
                    }

                    System.out.println("[System]: " + GREEN + "Auction list refreshed." + RESET);
                } catch (Exception e) {
                    System.out.println("[Error]: Auction list render error: " + RED + e.getMessage() + RESET);
                }

            } else if ("NEW_AUCTION_ADDED".equals(command)) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) response.getData();

                    String name    = (String) data.get("itemName");
                    String price   = String.format("%,.0f", ((Number) data.get("currentPrice")).doubleValue());
                    long   endTime = ((Number) data.get("endTime")).longValue();

                    MinimalItem newItem = new MinimalItem(name, price, endTime);
                    newItem.setOpacity(0);
                    mainTilePane.getChildren().add(0, newItem);

                    FadeTransition ft = new FadeTransition(Duration.millis(500), newItem);
                    ft.setToValue(1.0);
                    ft.play();

                    System.out.println("[System]: New auction added: " + YELLOW + name + RESET);
                } catch (Exception e) {
                    System.out.println("[Error]: New-auction render error: " + RED + e.getMessage() + RESET);
                }

            } else {
                // Forward unhandled commands to the shared dispatcher
                new ResponseDispatcher().dispatch(response, MainApplication.networkClient);
            }
        });
    }

    public void start() {
        setMainDock();
        setMainViewController();
        setupSearch();
        MainApplication.networkClient.setOnMessageReceived(this::handleServerResponse);
        MainApplication.networkClient.sendMessage("FETCH_AUCTIONS", "");
        System.out.println("[System]: " + GREEN + "User Controller started successfully." + RESET);
    }
}