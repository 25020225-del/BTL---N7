package gui;

import client.handler.ResponseDispatcher;
import gui.process.AnimateEffect;
import gui.process.AlertHelper;
import gui.process.Search;
import gui.widget.IconButton;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import model.User;
import network.NetworkMessage;
import gui.widget.MinimalItem;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static utils.ConsoleColors.*;

/**
 * Controller dedicated to the Bidder role within the client application.
 * Manages the marketplace grid, handles real-time UI updates for auctions
 * (adding, removing, and price changes), and delegates user actions to the server.
 */
public class ClientBidderController {

    private Parent mainView = null;
    private User currentUser;

    @FXML private VBox mainDock;
    @FXML private VBox mainViewController;
    @FXML private HBox searchBarContainer;
    @FXML private TilePane mainTilePane;

    @FXML private Button searchButton;
    @FXML private TextField searchField;

    // Side navigation buttons
    private IconButton toggleSearchButton = new IconButton("mdi2f-file-find-outline", "Search", "Search", "special-button");
    private IconButton account            = new IconButton("mdi2a-account", "Hello Bidder", "Account", "special-button");
    private IconButton toggleList         = new IconButton("mdi2m-menu", "List", "List", "special-button");

    // Mock deposit button for testing purposes
    private Button testDepositButton      = new IconButton("mdi2c-cash-plus", "Deposit 50,000 (Test)", "Test PayPal", "special-button");

    /**
     * Initializes the Bidder Controller and loads the main view layout.
     *
     * @param user The currently authenticated user instance.
     * @throws IOException If the corresponding FXML file cannot be loaded.
     */
    public ClientBidderController(User user) throws IOException {
        this.currentUser = user;
        this.account = new IconButton("mdi2a-account", "Hello, " + user.getName(), "Account");

        FXMLLoader fxmlMainView = new FXMLLoader(ClientBidderController.class.getResource("MainView.fxml"));
        fxmlMainView.setController(this);
        mainView = fxmlMainView.load();

        MainApplication.setNewScene(mainView);
    }

    /**
     * Configures the side navigation dock, attaching corresponding action events
     * to the menu buttons.
     */
    private void setMainDock() {
        mainDock.getChildren().add(account);
        mainDock.getChildren().addFirst(toggleSearchButton);
        mainDock.getChildren().addFirst(toggleList);
        mainDock.getChildren().add(testDepositButton);

        // Ensure all buttons share the same base styling
        for(Node k : mainDock.getChildren()){
            if(k instanceof Button && !k.getStyleClass().contains("special-button")){
                k.getStyleClass().add("special-button");
            }
        }

        testDepositButton.setOnAction(event -> {
            double testAmount = 50000;
            requestDeposit(testAmount);
        });

        toggleList.setUserData(true);
        toggleList.setOnAction(event -> {
            boolean isCollapsed = (boolean) toggleList.getUserData();
            for(Node k : mainDock.getChildren()) {
                if (k instanceof Button) {
                    Button b = (Button) k;
                    if (isCollapsed) {
                        b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    } else {
                        b.setContentDisplay(ContentDisplay.LEFT);
                    }
                }
            }
            toggleList.setUserData(!isCollapsed);
        });

        toggleSearchButton.setOnAction(event -> {
            AnimateEffect.fadeNode(searchBarContainer, !searchBarContainer.isVisible());
        });
    }

    /**
     * Prepares the main content area by clearing any residual child nodes.
     */
    private void setMainViewController() {
        if (mainTilePane != null) {
            mainTilePane.getChildren().clear();
        }
    }

    /**
     * Initializes the search bar functionality to filter items currently displayed in the grid.
     */
    private void setupSearch() {
        if (searchField == null || mainTilePane == null) return;

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

    /**
     * Dispatches a deposit request to the server.
     *
     * @param amount The numerical value of the deposit.
     */
    public void requestDeposit(double amount) {
        if (amount <= 0) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Error", "Deposit amount must be greater than 0.");
            return;
        }

        System.out.println("[Log]: Sending a deposit request of " + amount + " VND...");
        MainApplication.networkClient.sendMessage("CREATE_DEPOSIT", amount);
    }

    /**
     * Loads the detailed view for a specific item and injects the dynamic payload data.
     * Registers the controller to listen for real-time WebSocket price updates.
     *
     * @param auctionData A mapped dictionary of properties for the targeted auction.
     */
    private void openItemDetail(Map<String, Object> auctionData) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Productdetail.fxml"));
            Parent detailView = loader.load();

            ItemDetailController detailController = loader.getController();
            detailController.setProductData(auctionData);

            // CRITICAL: Bind this controller globally to receive live price streams
            client.handler.ClientAuctionHandler.activeDetailController = detailController;

            // Transition the UI
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(detailView);

            System.out.println("[Log]: Opened detail view for auction ID: " + auctionData.get("id"));

        } catch (IOException e) {
            System.out.println("[Error]: Failed to load Product Detail View: " + RED + e.getMessage() + RESET);
            e.printStackTrace();
        }
    }

    /**
     * Global router for server messages intended to manipulate the Bidder UI.
     *
     * @param response The structured NetworkMessage originating from the server.
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
                        long   endTime = ((Number) data.get("endTime")).longValue();

                        MinimalItem item = new MinimalItem(id, name, price, endTime);

                        // Attach the event to transition to the detail/chart view
                        item.getAuctionButton().setOnAction(e -> openItemDetail(data));

                        mainTilePane.getChildren().add(item);
                    }
                } catch (Exception e) {
                    System.out.println("[Error]: UI Render error during fetch: " + e.getMessage());
                }

            } else if ("NEW_AUCTION_ADDED".equals(command)) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) response.getData();

                    String id      = (String) data.get("id");
                    String name    = (String) data.get("itemName");
                    String price   = String.format("%,.0f", ((Number) data.get("currentPrice")).doubleValue());
                    long   endTime = ((Number) data.get("endTime")).longValue();

                    MinimalItem newItem = new MinimalItem(id, name, price, endTime);
                    newItem.getAuctionButton().setOnAction(e -> openItemDetail(data));

                    // Insert the new item at the top with a smooth fade-in animation
                    newItem.setOpacity(0);
                    mainTilePane.getChildren().add(0, newItem);

                    FadeTransition ft = new FadeTransition(Duration.millis(500), newItem);
                    ft.setToValue(1.0);
                    ft.play();
                } catch (Exception e) {
                    System.out.println("[Error]: Failed to render newly added auction: " + e.getMessage());
                }

            } else if ("REMOVE_AUCTION".equals(command)) {
                // BUG FIX: Automatically remove the product card from the UI grid when the Server broadcasts an expiration notice
                try {
                    String auctionIdToRemove = (String) response.getData();
                    mainTilePane.getChildren().removeIf(node -> auctionIdToRemove.equals(node.getId()));
                    System.out.println("[UI System]: Removed expired auction from grid: " + auctionIdToRemove);
                } catch (Exception e) {
                    System.out.println("[Error]: Failed to remove expired auction: " + e.getMessage());
                }

            } else {
                // Route unhandled commands to the centralized ResponseDispatcher
                new ResponseDispatcher().dispatch(response, MainApplication.networkClient);
            }
        });
    }

    /**
     * Bootstraps the controller logic upon initial navigation to this view.
     *
     * @throws IOException If UI components fail to load.
     */
    public void start() throws IOException {
        setMainDock();
        setMainViewController();
        setupSearch();

        MainApplication.networkClient.setOnMessageReceived(this::handleServerResponse);

        System.out.println("[Log]: Initializing Bidder View Components...");

        if (mainTilePane == null) {
            System.out.println("[Error]: " + RED + "Could not find Item Table (TilePane) in UI" + RESET);
            return;
        }

        // Request the latest active auctions from the server
        MainApplication.networkClient.sendMessage("FETCH_AUCTIONS", "");
        System.out.println("[System]: " + GREEN + "Bidder Controller started successfully. Awaiting data." + RESET);
    }
}