package gui;

import client.handler.ResponseDispatcher;
import gui.process.AnimateEffect;
import gui.process.AlertHelper;
import gui.widget.IconButton;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import model.User;
import network.NetworkMessage;
import gui.widget.MinimalItem;

import java.io.IOException;

import static utils.ConsoleColors.*;

public class ClientBidderController {

    private Parent mainView = null;
    private User currentUser;

    @FXML private VBox mainDock;
    @FXML private VBox mainViewController;
    @FXML private HBox searchBarContainer;

    @FXML private TilePane mainTilePane;

    private IconButton toggleSearchButton = new IconButton("mdi2f-file-find-outline", "search", "Search","special-button");
    private IconButton account = new IconButton("mdi2a-account", "Hello Bidder", "Account","special-button");
    private IconButton toggleList = new IconButton("mdi2m-menu", "List", "List","special-button");

    @FXML private Button searchButton;
    @FXML private TextField searchField;
    // TODO: Make a deposit UI
    private Button testDepositButton = new IconButton("mdi2c-cash-plus", "Deposit 50,000 (Test)", "Test PayPal");

    public ClientBidderController(User user) throws IOException {
        this.currentUser = user;
        this.account = new IconButton("mdi2a-account","Hello, " + user.getName(),"Account");
        FXMLLoader fxmlMainView = new FXMLLoader(ClientBidderController.class.getResource("MainView.fxml"));
        fxmlMainView.setController(this);
        mainView = fxmlMainView.load();
        MainApplication.setNewScene(mainView);
    }

    private void setMainDock() {
        mainDock.getChildren().add(account);
        mainDock.getChildren().addFirst(toggleSearchButton);
        mainDock.getChildren().addFirst(toggleList);
        //test deposit
        mainDock.getChildren().add(testDepositButton);

        for(Node k : mainDock.getChildren()){
            if(k instanceof Button){
                k.getStyleClass().add("special-button");
            }
        }
        //test deposit
        testDepositButton.setOnAction(event -> {
            double testAmount = 50000;
            requestDeposit(testAmount);
        });

        toggleList.setUserData(true);
        searchField.setOnAction(event -> {
            searchButton.getOnAction().handle(null);
        });
        toggleList.setOnAction(event -> {
            for(Node k : mainDock.getChildren()) {
                if (k instanceof Button) {
                    Button b = (Button) k;
                    if ((boolean)  toggleList.getUserData()) {
                        b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    }
                    else {
                        b.setContentDisplay(ContentDisplay.LEFT);
                    }
                }
            }
            toggleList.setUserData(!((boolean) toggleList.getUserData()));
        });
        toggleSearchButton.setOnAction(event -> {
            AnimateEffect.fadeNode(searchBarContainer, !searchBarContainer.isVisible());
        });
        searchButton.setOnAction(event -> {
            String keyword = searchField.getText();
            System.out.println("[Log]: Searching for: " + YELLOW + keyword + RESET);
            AnimateEffect.showOrHideItem(mainTilePane, keyword);
        });
    }

    private void setMainViewController() {
        mainTilePane.getChildren().clear();
    }

    public void requestDeposit(double amount) {
        if (amount <= 0) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Error", "Deposit amount must be greater than 0");
            return;
        }

        System.out.println("[Log]: Sending a deposit request of " + amount + " VND...");

        MainApplication.networkClient.sendMessage("CREATE_DEPOSIT", amount);
    }

    // Handle server sent data
    private void handleServerResponse(NetworkMessage response) {
        javafx.application.Platform.runLater(() -> {
            if ("FETCH_AUCTIONS_SUCCESS".equals(response.getCommand())) {
                try {
                    // Safe casting from Jackson List <Map>
                    @SuppressWarnings("unchecked")
                    java.util.List<java.util.Map<String, Object>> auctions =
                            (java.util.List<java.util.Map<String, Object>>) response.getData();

                    mainTilePane.getChildren().clear();

                    // Browse through each product and design the UI
                    for (java.util.Map<String, Object> data : auctions) {
                        String name = (String) data.get("itemName");
                        // Price format
                        String price = String.format("%,.0f", ((Number) data.get("currentPrice")).doubleValue());
                        long endTime = ((Number) data.get("endTime")).longValue();

                        // Call the MinimalItem widget
                        MinimalItem item = new gui.widget.MinimalItem(name, price, endTime);
                        mainTilePane.getChildren().add(item);
                    }
                } catch (Exception e) {
                    System.out.println("[Error]: UI Render error: " + e.getMessage());
                }
            }
            // IF NECESSARY: Route other commands back to the ResponseDispatcher to preserve system functionality
            else {
                new ResponseDispatcher().dispatch(response, MainApplication.networkClient);
            }
        });
    }

    public void start() throws IOException {
        setMainDock();
        setMainViewController();
        MainApplication.networkClient.setOnMessageReceived(this::handleServerResponse);

        System.out.println("[Log]: Initializing Bidder View Components...");

        if (mainTilePane == null) {
            System.out.println("[Error]: " + RED + "Could not find Item Table (TilePane) in UI" + RESET);
            return;
        }
        MainApplication.networkClient.sendMessage("FETCH_AUCTIONS", "");
        System.out.println("[System]: " + GREEN + "Bidder Controller started successfully. Table updated" + RESET);
    }
}