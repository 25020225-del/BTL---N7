package gui;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import client.network.NetworkClient;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

public class ClientSellerController {

    private Parent mainView;

    @FXML private VBox mainDock;
    @FXML private VBox mainViewController;

    @FXML private TilePane mainTilePane;

    private Button account = (Button) WidgetFactory.createButton("mdi2a-account","Hello Seller","Account");
    private Button toggleList = (Button) WidgetFactory.createButton("mdi2m-menu","List","List");
    private Button createTransaction =  (Button) WidgetFactory.createButton("mdi2a-archive-plus-outline","Create Transaction","Create Transaction");

    public ClientSellerController() throws IOException {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("/gui/MainView.fxml"));
        loader.setController(this);
        mainView = loader.load();
        MainApplication.setNewScene(mainView);
    }

    private void setMainDock() {
        mainDock.getChildren().clear();
        mainDock.getChildren().add(account);
        mainDock.getChildren().addFirst(createTransaction);
        mainDock.getChildren().addFirst(toggleList);
        for(Node k : mainDock.getChildren()){
            if(k instanceof Button){
                k.getStyleClass().add("special-button");
            }
        }

        toggleList.setUserData(true);
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
    }
    private void setMainViewController() {
    }

    public void start() {
        setMainDock();
        setMainViewController();
    }

    public void createAuction() {
        Map<String, String> auctionData = new HashMap<>();
        auctionData.put("itemName", "Lông dái Ronaldo");
        auctionData.put("description", "Còn thơm mùi nước đái");
        auctionData.put("startingPrice", "2500000000");
        auctionData.put("bidIncrement", "5000000");
        auctionData.put("durationMinutes", "69");

        System.out.println("[Log]: Sending creating auction request...");
        MainApplication.networkClient.sendMessage("CREATE_AUCTION", auctionData);
    }
}
