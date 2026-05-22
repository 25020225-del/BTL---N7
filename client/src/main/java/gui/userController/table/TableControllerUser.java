package gui.userController.table;

import client.handler.AuctionEventBus;
import client.service.AuctionService;
import com.fasterxml.jackson.core.type.TypeReference;
import gui.process.AlertHelper;
import gui.widget.item.MinimalItem;
import gui.widget.item.MinimalItemUser;
import gui.widget.item.MinimalSellerItem;
import gui.widget.item.MinimalUser;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import model.auction.Auction;
import model.item.Item;
import model.item.ItemFactory;
import model.user.User;
import network.NetworkMessage;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TableControllerUser extends TableController {
    @FXML protected void initialize() {
        setupSearch();
        AuctionEventBus.addListener(AuctionEventBus.FETCH_AUCTIONS_SUCCESS, event -> {
            try {
                NetworkMessage response = (NetworkMessage) event.getNewValue();
                List<Map<String, Object>> auctions = mapper.convertValue(
                        response.getData(),
                        new TypeReference<List<Map<String, Object>>>() {}
                );
                List<MinimalItem> items = new ArrayList<>();
                for(Map<String, Object> auction : auctions) {
                    items.add(buildMinimalItem(auction));
                }
                Platform.runLater(() -> {addAllAuction(items);});
            } catch (Exception e) {
                log.error("[Client] FETCH_AUCTIONS_SUCCESS parse error: {}", e.getMessage());
            }
        });
        AuctionEventBus.addListener(AuctionEventBus.FETCH_MY_AUCTIONS_SUCCESS, event -> {
            try {
                NetworkMessage response = (NetworkMessage) event.getNewValue();
                List<Map<String,Object>> auctions = mapper.convertValue(
                        response.getData(),
                        new  TypeReference<List<Map<String, Object>>>() {}
                );
                List<MinimalItem> items = new ArrayList<>();
                for(Map<String, Object> auction : auctions) {
                    MinimalItem item = MinimalSellerItem.newMinimalSellerItemFromMap(auction);
                    items.add(item);
                }
                Platform.runLater(() -> {addAllAuction(items);});
            } catch (IllegalArgumentException e) {
                log.error("[Client] FETCH_MY_AUCTIONS_SUCCESS parse error: {}", e.getMessage());
            }
        });
        AuctionEventBus.addListener("NEW_AUCTION_ADDED", event -> {
            try {
                NetworkMessage response = (NetworkMessage) event.getNewValue();
                Map<String, Object> auction = mapper.convertValue(
                        response.getData(),
                        new TypeReference<Map<String, Object>>() {}
                );
                Platform.runLater(() -> {addNewAuction(buildMinimalItem(auction));});

            } catch (Exception e) {
                log.error("[Client] NEW_AUCTION_ADDED parse error: {}", e.getMessage());
            }
        });
        AuctionEventBus.addListener("REMOVE_AUCTION", event -> {
            NetworkMessage response = (NetworkMessage) event.getNewValue();
            String auctionIdToRemove = (String) response.getData();
            Platform.runLater(() -> {removeAuction(auctionIdToRemove);});

        });
        AuctionEventBus.addListener("EDIT_SUCCESS", event -> {

            NetworkMessage response = (NetworkMessage) event.getNewValue();
            AlertHelper.showAlert(Alert.AlertType.INFORMATION, "Success", response.getData().toString());
            AuctionService.fetchAuctions();
        });
        AuctionEventBus.addListener("DELETE_SUCCESS", event -> {

            NetworkMessage response = (NetworkMessage) event.getNewValue();
            AlertHelper.showAlert(Alert.AlertType.INFORMATION, "Success", response.getData().toString());
            AuctionService.fetchAuctions();
        });
    }

    protected Auction auctionFromMap(Map<String, Object> map) {
        Auction auction = new Auction();
        auction.setId((String) map.get("id"));

        Item item = ItemFactory.createItem(
                ItemFactory.TYPE_TANGIBLE,
                "ITM-" + map.get("id"),
                (String) map.get("itemName"),
                (String) map.get("description"),
                0
        );
        item.setImageUrl((String) map.get("imageUrl"));
        auction.setItem(item);

        User seller = new User();
        seller.setId((String) map.get("sellerId"));
        auction.setSeller(seller);

        auction.setCurrentPrice(((Number) map.get("currentPrice")).longValue());
        auction.setEndTime(
                Instant.ofEpochMilli(((Number) map.get("endTime")).longValue())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
        );

        return auction;
    }
    protected MinimalItemUser buildMinimalItem(Map<String, Object> map) {
        String id       = (String) map.get("id");
        String name     = (String) map.get("itemName");
        String imageUrl = (String) map.get("imageUrl");
        String sellerId = (String) map.get("sellerId");
        double price    = ((Number) map.get("currentPrice")).doubleValue();
        long endTime    = ((Number) map.get("endTime")).longValue();

        Auction auction = auctionFromMap(map);

        MinimalItemUser item = new MinimalItemUser(id, imageUrl, name,
                String.format("%,.0f", price), endTime);
        item.getAuctionButton().setOnAction(e -> openItemDetail(auction));
        return item;
    }
}
