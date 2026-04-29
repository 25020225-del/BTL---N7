package gui;

import client.network.NetworkClient;
import gui.process.AlertHelper;
import gui.widget.IconButton;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import model.User;
import model.Item;
import network.NetworkMessage;
import gui.process.AlertHelper;
import javafx.scene.control.Alert;

import java.io.IOException;

import static utils.ConsoleColors.*;

public class ClientUserController {

    private Parent mainView;
    private Parent createAuctionView;
    private VBox marketplaceView;

    private User currentUser;

    @FXML private VBox mainDock;
    @FXML private VBox mainViewController; // Content change display

    @FXML private TilePane mainTilePane;

    // For search field
    @FXML private javafx.scene.control.TextField searchField;
    @FXML private javafx.scene.control.Button searchButton;

    // LEFT-SIDE NAVIGATION BUTTONS
    private IconButton accountBtn;
    private IconButton marketplaceBtn = new IconButton("mdi2s-storefront-outline", "Chợ đấu giá", "Marketplace", "special-button");
    private IconButton createAuctionBtn = new IconButton("mdi2a-archive-plus-outline", "Đăng bán", "Create Auction", "special-button");
    private IconButton depositBtn = new IconButton("mdi2c-cash-plus", "Deposit 50000 VND (TEST)", "Deposit", "special-button");
    private IconButton testCreateAuctionBtn = new IconButton("mdi2b-bug", "Create Auction (TEST)", "Test Create", "special-button");

    public ClientUserController(User user) throws IOException {
        this.currentUser = user;
        this.accountBtn = new IconButton("mdi2a-account", "Hello, " + user.getName(), "Account");

        // Load dashboard
        FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("MainView.fxml"));
        mainLoader.setController(this);
        this.mainView = mainLoader.load();

        // Load seller UI
        FXMLLoader sellerLoader = new FXMLLoader(getClass().getResource("SellerCreateAuction.fxml"));
        sellerLoader.setController(this); // Use this controller to handle Create button event
        this.createAuctionView = sellerLoader.load();

        // Save the default auction view
        this.marketplaceView = new VBox(mainViewController.getChildren().toArray(new Node[0]));

        MainApplication.setNewScene(mainView);
    }

    public void start() {
        setMainDock();

        // Send a request to fetch market data as soon as the app opens
        MainApplication.networkClient.setOnMessageReceived(this::handleServerResponse);
        MainApplication.networkClient.sendMessage("FETCH_AUCTIONS", "");
        search();
    }

    private void setMainDock() {
        mainDock.getChildren().clear();
        mainDock.getChildren().addAll(accountBtn, marketplaceBtn, createAuctionBtn, depositBtn, testCreateAuctionBtn);

        // Auction list UI button
        marketplaceBtn.setOnAction(e -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(marketplaceView);
            // Send a command to reload the list
            MainApplication.networkClient.sendMessage("FETCH_AUCTIONS", "");
        });

            // Creating auction UI button
        createAuctionBtn.setOnAction(e -> {
            mainViewController.getChildren().clear();
            mainViewController.getChildren().add(createAuctionView);
        });

        // Deposit button
        depositBtn.setOnAction(e -> {
            MainApplication.networkClient.sendMessage("CREATE_DEPOSIT", 50000); // Test
        });

        testCreateAuctionBtn.setOnAction(event -> {
            java.util.Map<String, String> dummyData = new java.util.HashMap<>();
            String testItemName = "CẶC" + (System.currentTimeMillis() % 10000);

            dummyData.put("itemName", testItemName);
            dummyData.put("description", "ĐỊT MẸ MÀY");
            dummyData.put("startingPrice", "50000");
            dummyData.put("bidIncrement", "5000");
            dummyData.put("durationMinutes", "60");

            System.out.println("[Log]: Sent creating auction request for " + testItemName);
            MainApplication.networkClient.sendMessage("CREATE_AUCTION", dummyData);
        });
    }

    private void search() {
        // CÀI ĐẶT LỌC TÌM KIẾM TRỰC TIẾP
        if (searchField != null && mainTilePane != null) {

            // 1. Lắng nghe từng chữ người dùng gõ
            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                String keyword = newValue.trim();

                // Duyệt qua toàn bộ sản phẩm trên bảng
                for (javafx.scene.Node node : mainTilePane.getChildren()) {
                    if (node instanceof gui.widget.MinimalItem) {
                        gui.widget.MinimalItem item = (gui.widget.MinimalItem) node;

                        // Sử dụng class Search của nhóm bạn để quét chữ (không bị lỗi getItemName nữa)
                        boolean isMatch = gui.process.Search.searchText(keyword, item);

                        // Ẩn/Hiện sản phẩm
                        item.setVisible(isMatch);
                        item.setManaged(isMatch);
                    }
                }
            });

            // 2. Khi bấm Enter trong ô nhập liệu thì giả lập hành động bấm nút SEARCH
            searchField.setOnAction(event -> {
                if (searchButton != null) searchButton.fire();
            });

        } else {
            System.out.println("[Cảnh báo]: Không tìm thấy searchField hoặc mainTilePane! Hãy kiểm tra lại @FXML.");
        }

        // 3. Logic của nút SEARCH
        if (searchButton != null) {
            searchButton.setOnAction(event -> {
                System.out.println("[Log]: Đang tìm kiếm từ khóa: " + searchField.getText());
            });
        }
    }

    @FXML
    public void handleSubmitAuction(javafx.event.ActionEvent event) {
        System.out.println("[Log]: Starting to process creating auction...");

        try {
            // Set button as checkpoint
            javafx.scene.Node sourceBtn = (javafx.scene.Node) event.getSource();

            // Get UI root
            javafx.scene.Parent root = sourceBtn.getParent();
            while (root.getParent() != null) {
                root = root.getParent();
            }

            // Use scanner to find data fields
            javafx.scene.control.TextField inputName = (javafx.scene.control.TextField) getNodeById(root, "sellerCreateAuction_itemName");

            javafx.scene.control.TextArea inputDesc = (javafx.scene.control.TextArea) getNodeById(root, "sellerCreateAuction_description");

            javafx.scene.control.TextField inputStartPrice = (javafx.scene.control.TextField) getNodeById(root, "sellerCreateAuction_startPrice");
            javafx.scene.control.TextField inputBidInc = (javafx.scene.control.TextField) getNodeById(root, "sellerCreateAuction_bidIncrement");

            javafx.scene.control.DatePicker startDate = (javafx.scene.control.DatePicker) getNodeById(root, "sellerCreateAuction_startDate");
            javafx.scene.control.TextField startHour = (javafx.scene.control.TextField) getNodeById(root, "sellerCreateAuction_startHour");
            javafx.scene.control.TextField startMinute = (javafx.scene.control.TextField) getNodeById(root, "sellerCreateAuction_startMinute");

            javafx.scene.control.DatePicker endDate = (javafx.scene.control.DatePicker) getNodeById(root, "sellerCreateAuction_endDate");
            javafx.scene.control.TextField endHour = (javafx.scene.control.TextField) getNodeById(root, "sellerCreateAuction_endHour");
            javafx.scene.control.TextField endMinute = (javafx.scene.control.TextField) getNodeById(root, "sellerCreateAuction_endMinute");

            // If scanner can't find
            if (inputDesc == null || inputName == null) {
                gui.process.AlertHelper.showAlert(javafx.scene.control.Alert.AlertType.ERROR, "UI Error", "Cannot find fields");
                return;
            }

            // Safely get data
            String name = inputName.getText().trim();
            String desc = inputDesc.getText().trim();
            String startPrice = inputStartPrice.getText().trim();
            String bidInc = inputBidInc.getText().trim();

            if (name.isEmpty() || desc.isEmpty() || startPrice.isEmpty() || bidInc.isEmpty()) {
                gui.process.AlertHelper.showAlert(javafx.scene.control.Alert.AlertType.WARNING, "Information Error", "Please fulfill required fields");
                return;
            }

            if (startDate.getValue() == null || endDate.getValue() == null) {
                gui.process.AlertHelper.showAlert(javafx.scene.control.Alert.AlertType.WARNING, "Time Error", "Please select starting time and end time");
                return;
            }

            int sHour = Integer.parseInt(startHour.getText().trim());
            int sMin = Integer.parseInt(startMinute.getText().trim());
            java.time.LocalDateTime startDT = java.time.LocalDateTime.of(startDate.getValue(), java.time.LocalTime.of(sHour, sMin));

            int eHour = Integer.parseInt(endHour.getText().trim());
            int eMin = Integer.parseInt(endMinute.getText().trim());
            java.time.LocalDateTime endDT = java.time.LocalDateTime.of(endDate.getValue(), java.time.LocalTime.of(eHour, eMin));

            long duration = java.time.Duration.between(startDT, endDT).toMinutes();

            if (duration <= 0) {
                gui.process.AlertHelper.showAlert(javafx.scene.control.Alert.AlertType.WARNING, "Time Error", "End time must be later than starting time");
                return;
            }

            Double.parseDouble(startPrice);
            Double.parseDouble(bidInc);

            // Send to server
            java.util.Map<String, String> auctionData = new java.util.HashMap<>();
            auctionData.put("itemName", name);
            auctionData.put("description", desc);
            auctionData.put("startingPrice", startPrice);
            auctionData.put("bidIncrement", bidInc);
            auctionData.put("durationMinutes", String.valueOf(duration));

            System.out.println("[System]: Sending creating auction request to server...");
            gui.MainApplication.networkClient.sendMessage("CREATE_AUCTION", auctionData);

            // Clear form after being sent
            inputName.clear();
            inputDesc.clear();
            inputStartPrice.clear();
            inputBidInc.clear();
            startHour.clear(); startMinute.clear();
            endHour.clear(); endMinute.clear();
            startDate.setValue(null); endDate.setValue(null);

            // Switch to marketplace UI
            if (marketplaceBtn != null) marketplaceBtn.fire();

        } catch (NumberFormatException e) {
            gui.process.AlertHelper.showAlert(javafx.scene.control.Alert.AlertType.ERROR, "Lỗi định dạng", "Giá tiền và Giờ/Phút phải là số!");
        } catch (java.time.DateTimeException e) {
            gui.process.AlertHelper.showAlert(javafx.scene.control.Alert.AlertType.ERROR, "Lỗi thời gian", "Giờ/Phút không hợp lý (0-23, 0-59).");
        } catch (Exception e) {
            System.out.println("[System]: Error: " + RED + e.getMessage() + RESET);
            e.printStackTrace();
        }
    }

    // Memory Scan Method: Directly locate the input field on the currently displayed interface
    private javafx.scene.Node getNodeById(javafx.scene.Node node, String id) {
        if (id.equals(node.getId())) return node;
        if (node instanceof javafx.scene.Parent) {
            for (javafx.scene.Node child : ((javafx.scene.Parent) node).getChildrenUnmodifiable()) {
                javafx.scene.Node found = getNodeById(child, id);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void handleServerResponse(NetworkMessage response) {
        Platform.runLater(() -> {
            String command = response.getCommand();

            if ("FETCH_AUCTIONS_SUCCESS".equals(command)) {
                try {
                    // Delete old auction list
                    mainTilePane.getChildren().clear();

                    // Safely parse data array from Jackson JSON
                    @SuppressWarnings("unchecked")
                    java.util.List<java.util.Map<String, Object>> auctions =
                            (java.util.List<java.util.Map<String, Object>>) response.getData();

                    // Duyệt qua từng sản phẩm và vẽ lên UI
                    for (java.util.Map<String, Object> data : auctions) {
                        String name = (String) data.get("itemName");
                        // Định dạng giá tiền có dấu phẩy (vd: 50,000)
                        String price = String.format("%,.0f", ((Number) data.get("currentPrice")).doubleValue());
                        long endTime = ((Number) data.get("endTime")).longValue();

                        // Gọi Widget MinimalItem và thêm vào bảng (TilePane)
                        gui.widget.MinimalItem item = new gui.widget.MinimalItem(name, price, endTime);
                        mainTilePane.getChildren().add(item);
                    }
                } catch (Exception e) {
                    System.out.println("[System]: Auction List UI Render Error: " + RED + e.getMessage() + RESET);
                }

            } else if ("NEW_AUCTION_ADDED".equals(command)) {
                try {
                    // Ép kiểu gói dữ liệu của 1 sản phẩm mới vừa được broadcast
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.getData();

                    String name = (String) data.get("itemName");
                    String price = String.format("%,.0f", ((Number) data.get("currentPrice")).doubleValue());
                    long endTime = ((Number) data.get("endTime")).longValue();

                    // Create new Widget
                    gui.widget.MinimalItem newItem = new gui.widget.MinimalItem(name, price, endTime);

                    // Add to the first index of table
                    mainTilePane.getChildren().add(0, newItem);

                    // Fade-in effect to announce to user
                    newItem.setOpacity(0);
                    javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(500), newItem);
                    ft.setToValue(1.0);
                    ft.play();

                    System.out.println("[System]: New auction of: " + YELLOW + name + RESET + " loaded");

                } catch (Exception e) {
                    System.out.println("[System]: Render error: " + RED + e.getMessage() + RESET);
                }

            } else {
                // Send other commands to ResponseDispatcher
                new client.handler.ResponseDispatcher().dispatch(response, gui.MainApplication.networkClient);
            }
        });
    }
}