package gui.userController.table;

import client.handler.AuctionEventBus;
import client.service.AuctionService;
import com.fasterxml.jackson.core.type.TypeReference;
import gui.process.AlertUtils;
import gui.widget.Selector;
import gui.widget.item.MinimalItem;
import gui.widget.item.MinimalItemUser;
import gui.widget.item.MinimalSellerItem;
import javafx.application.Platform;
import javafx.fxml.FXML;
import model.auction.Auction;
import model.item.ItemFactory;
import network.NetworkMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Public-facing client portfolio display interface. Intercepts asynchronous catalog telemetry frames
 * to refresh active asset listings, bidding metrics, and individual tracking collections.
 */
public class TableControllerUser extends TableController {
    private Selector chooseType;

    @FXML
    protected void initialize() {
        removeAllSelectors();
        chooseType = new Selector("Type", "", ItemFactory.TYPE_TANGIBLE, ItemFactory.TYPE_DIGITAL, ItemFactory.TYPE_SERVICE);
        chooseType.setOnChange(event1 -> searchByProperties("itemType", event1));
        addSelector(chooseType);
        setupSearch();

        AuctionEventBus.addListener(AuctionEventBus.FETCH_AUCTIONS_SUCCESS, event -> {
            try {
                NetworkMessage response = (NetworkMessage) event.getNewValue();
                List<Map<String, Object>> auctions = mapper.convertValue(
                        response.getData(),
                        new TypeReference<List<Map<String, Object>>>() {}
                );
                List<MinimalItem> items = new ArrayList<>();
                for (Map<String, Object> auction : auctions) {
                    items.add(buildMinimalItem(auction));
                }
                Platform.runLater(() -> addAllItem(items));
            } catch (Exception e) {
                log.error("Failed to parse catalog matrix mapping payload on user pipeline: {}", e.getMessage());
            }
        });

        AuctionEventBus.addListener(AuctionEventBus.FETCH_MY_AUCTIONS_SUCCESS, event -> {
            try {
                NetworkMessage response = (NetworkMessage) event.getNewValue();
                List<Map<String, Object>> auctions = mapper.convertValue(
                        response.getData(),
                        new TypeReference<List<Map<String, Object>>>() {}
                );
                List<MinimalItem> items = new ArrayList<>();
                for (Map<String, Object> auction : auctions) {
                    items.add(MinimalSellerItem.newMinimalSellerItemFromMap(auction));
                }
                Platform.runLater(() -> addAllItem(items));
            } catch (IllegalArgumentException e) {
                log.error("Failed to parse user-specific historical asset vector listings: {}", e.getMessage());
            }
        });

        AuctionEventBus.addListener("NEW_AUCTION_ADDED", event -> {
            try {
                NetworkMessage response = (NetworkMessage) event.getNewValue();
                Map<String, Object> auction = mapper.convertValue(
                        response.getData(),
                        new TypeReference<Map<String, Object>>() {}
                );
                Platform.runLater(() -> addNewItem(buildMinimalItem(auction)));
            } catch (Exception e) {
                log.error("Failed to parse localized runtime entry allocation packet: {}", e.getMessage());
            }
        });

        AuctionEventBus.addListener("REMOVE_AUCTION", event -> {
            NetworkMessage response = (NetworkMessage) event.getNewValue();
            String auctionIdToRemove = (String) response.getData();
            Platform.runLater(() -> removeItem(auctionIdToRemove));
        });

        AuctionEventBus.addListener("EDIT_SUCCESS", event -> {
            NetworkMessage response = (NetworkMessage) event.getNewValue();
            AlertUtils.showInfo("Success", response.getData().toString());
            AuctionService.fetchAuctions();
        });

        AuctionEventBus.addListener("DELETE_SUCCESS", event -> {
            NetworkMessage response = (NetworkMessage) event.getNewValue();
            AlertUtils.showInfo("Success", response.getData().toString());
            AuctionService.fetchAuctions();
        });
    }

    protected MinimalItemUser buildMinimalItem(Map<String, Object> map) {
        String id = (String) map.get("id");
        String name = (String) map.get("itemName");
        String itemType = (String) map.get("itemType");
        String imageUrl = (String) map.get("imageUrl");

        Object currentPriceVal = map.get("currentPrice");
        double price = currentPriceVal instanceof Number ? ((Number) currentPriceVal).doubleValue() : 0.0;

        Object endTimeVal = map.get("endTime");
        long endTime = endTimeVal instanceof Number ? ((Number) endTimeVal).longValue() : 0L;

        Auction auction = Auction.buildAuctionFromMap(map);

        MinimalItemUser item = new MinimalItemUser(id, imageUrl, name, itemType, String.format("%,.0f", price), endTime);
        item.getAuctionButton().setOnAction(e -> openItemDetail(auction));
        return item;
    }
}