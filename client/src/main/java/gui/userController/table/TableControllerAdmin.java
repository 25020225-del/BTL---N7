package gui.userController.table;

import client.handler.AuctionEventBus;
import gui.widget.AdminUserItem;
import gui.widget.item.MinimalItemAdmin;
import gui.widget.item.MinimalItemUser;
import javafx.application.Platform;
import javafx.fxml.FXML;
import model.auction.Auction;
import model.item.Item;
import model.item.ItemFactory;
import model.user.User;
import network.NetworkMessage;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

public class TableControllerAdmin extends TableController {

    public void addNewUser(AdminUserItem user) {
        mainTilePane.getChildren().addFirst(user);
    }
    @FXML protected void initialize() {
        AuctionEventBus.addListener("FETCH_AUCTIONS_SUCCESS", event -> {
            NetworkMessage networkMessage = (NetworkMessage) event.getNewValue();
            List<Map<String,Object>> data = (List<Map<String,Object>>) networkMessage.getData();
            Platform.runLater(() -> {
                mainTilePane.getChildren().clear();
                data.forEach(map -> {
                    addNewAuction(buildMinimalItem(map));
                });
            });
        });
    }

    protected Auction auctionFromMap(Map<String, Object> map) {
        Auction auction = new Auction();
        auction.setId((String) map.get("id"));

        Item item = ItemFactory.createItem(
                ItemFactory.TYPE_TANGIBLE,
                "ITM-" + map.get("id"),
                (String) map.get("itemName"),
                null,
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
    protected MinimalItemAdmin buildMinimalItem(Map<String, Object> map) {
        String id       = (String) map.get("id");
        String name     = (String) map.get("itemName");

        Auction auction = auctionFromMap(map);

        MinimalItemAdmin item = new MinimalItemAdmin(id, name, "");
        item.addAdminOptions(id);
        return item;
    }
}
