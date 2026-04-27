package gui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import model.User;

import java.io.IOException;

import static utils.ConsoleColors.*;

public class ClientBidderController {

    private Parent mainView = null;
    private User currentUser;

    @FXML private VBox mainDock;
    @FXML private VBox mainViewController;
    @FXML private HBox searchBarContainer;

    @FXML private TilePane mainTilePane;

    private Button toggleSearchButton = (Button) WidgetFactory.createButton("mdi2f-file-find-outline", "search", "Search");
    private Button account;
    private Button toggleList = (Button) WidgetFactory.createButton("mdi2m-menu","List","List");

    @FXML private Button searchButton;
    @FXML private TextField searchField;
    // TODO: Make a deposit UI
    private Button testDepositButton = (Button) WidgetFactory.createButton("mdi2c-cash-plus", "Deposit 50,000 (Test)", "Test PayPal");

    public ClientBidderController(User user) throws IOException {
        this.currentUser = user;
        this.account = (Button) WidgetFactory.createButton("mdi2a-account","Hello, " + user.getName(),"Account");
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
            System.out.println("[Log]: Sending deposit request of " + testAmount + " VND to Server...");

            MainApplication.networkClient.sendMessage("CREATE_DEPOSIT", testAmount);
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
        final long TWO_MINUTES = 2 * 60 * 1000;
        mainTilePane.getChildren().add(WidgetFactory.createMinimalItem("Máy xay sinh tố mèo","30000",System.currentTimeMillis() + TWO_MINUTES));
        mainTilePane.getChildren().add(WidgetFactory.createMinimalItem("Đùi gà tẩm bột chiên xù","40000",System.currentTimeMillis() + TWO_MINUTES));
        mainTilePane.getChildren().add(WidgetFactory.createMinimalItem("Máy bay đồ chơi mini","1200000",System.currentTimeMillis() + TWO_MINUTES));
        mainTilePane.getChildren().add(WidgetFactory.createMinimalItem("Thịt cừu nướng","127000",System.currentTimeMillis() + TWO_MINUTES));
        mainTilePane.getChildren().add(WidgetFactory.createMinimalItem("Mỡ lợn","80000",System.currentTimeMillis() + TWO_MINUTES));
        mainTilePane.getChildren().add(WidgetFactory.createMinimalItem("Đầu cá","35000",System.currentTimeMillis() + TWO_MINUTES));
        mainTilePane.getChildren().add(WidgetFactory.createMinimalItem("Dương vật ngựa","366769",System.currentTimeMillis() + TWO_MINUTES));
    }

    public void requestDeposit(double amount) {
        if (amount <= 0) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Error", "Deposit amount must be greater than 0");
            return;
        }

        System.out.println("[Log]: Sending a deposit request " + amount + " VND...");

        MainApplication.networkClient.sendMessage("CREATE_DEPOSIT", amount);
    }

    public void start() throws IOException {
        setMainDock();
        setMainViewController();
        System.out.println("[Log]: Initializing Bidder View Components...");

        if (mainTilePane == null) {
            System.out.println("[Error]: " + RED + "Could not find Item Table (TilePane) in UI" + RESET);
            return;
        }
        System.out.println("[System]: " + GREEN + "Bidder Controller started successfully. Table updated" + RESET);
    }
}