package gui;

import gui.process.AlertHelper;
import gui.widget.IconButton;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import model.User;
import javafx.scene.layout.HBox;
import javafx.scene.control.Label;

import java.io.IOException;

public class ClientAdminController {

    private Parent mainView;
    private User currentAdmin;

    @FXML
    private VBox mainDock;
    @FXML private VBox mainViewController;

    @FXML private TilePane mainTilePane;

    private IconButton account = new IconButton("mdi2a-account", "Hello Admin", "Account", "special-button");
    private IconButton toggleList = new IconButton("mdi2m-menu", "List", "List", "special-button");
    private IconButton accountList = new IconButton("mdi2a-account-box-multiple-outline", "Account", "Account", "special-button");
    private IconButton itemList = new IconButton("mdi2a-archive-settings-outline", "Item", "Item", "special-button");

    public ClientAdminController(User user) throws IOException {
        this.currentAdmin = user;
        this.account = new IconButton("mdi2a-account", "Admin: " + user.getName(), "Account");
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("/gui/MainView.fxml"));
        loader.setController(this);
        mainView = loader.load();
        MainApplication.setNewScene(mainView);
    }

    private void setMainDock() {
        mainDock.getChildren().add(account);
        mainDock.getChildren().addFirst(itemList);
        mainDock.getChildren().addFirst(accountList);
        mainDock.getChildren().addFirst(toggleList);

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
        itemList.setOnAction(event -> {
            System.out.println("[System]: Loading pending auctions...");
            // Get data from server
            MainApplication.networkClient.sendMessage("FETCH_PENDING_AUCTIONS", "");
        });

        // Listen to server response
        MainApplication.networkClient.setOnMessageReceived(response -> {
            javafx.application.Platform.runLater(() -> {
                String command = response.getCommand();

                // 1. Get list and Render
                if ("FETCH_AUCTIONS_SUCCESS".equals(command)) { // Use the same key as bidder
                    mainTilePane.getChildren().clear();

                    java.util.List<java.util.Map<String, Object>> auctions =
                            (java.util.List<java.util.Map<String, Object>>) response.getData();

                    for (java.util.Map<String, Object> data : auctions) {
                        String id = (String) data.get("id");
                        String name = (String) data.get("itemName");

                        // Create a display box for the admin
                        VBox itemBox = new VBox(10);
                        itemBox.setStyle("-fx-border-color: #aaa; -fx-padding: 10; -fx-background-color: white;");
                        Label lblName = new Label("Item: " + name);

                        Button btnApprove = new Button("Aprrove");
                        btnApprove.setStyle("-fx-background-color: green; -fx-text-fill: white;");

                        Button btnReject = new Button("Reject");
                        btnReject.setStyle("-fx-background-color: red; -fx-text-fill: white;");

                        // Handle the “Approve” button click event
                        btnApprove.setOnAction(e -> {
                            gui.MainApplication.networkClient.sendMessage("APPROVE_AUCTION", id);
                        });

                        // Handle the “Reject” button click event
                        btnReject.setOnAction(e -> {
                            gui.MainApplication.networkClient.sendMessage("REJECT_AUCTION", id);
                        });

                        HBox btnGroup = new HBox(10, btnApprove, btnReject);
                        itemBox.getChildren().addAll(lblName, btnGroup);
                        mainTilePane.getChildren().add(itemBox);
                    }
                }
                // 2. Receive a notification of successful approval
                else if ("ADMIN_ACTION_SUCCESS".equals(command)) {
                    AlertHelper.showAlert(javafx.scene.control.Alert.AlertType.INFORMATION, "Success", response.getData().toString());
                    // Automatically reload the page after browsing
                    itemList.getOnAction().handle(null);
                }
                else {
                    // Navigate to other commands
                    new client.handler.ResponseDispatcher().dispatch(response, gui.MainApplication.networkClient);
                }
            });
        });
    }

    public void start() {
        setMainDock();
        setMainViewController();
    }
}
