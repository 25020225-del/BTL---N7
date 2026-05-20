package gui.userController.table;

import client.handler.AuctionEventBus;
import client.service.AdminService;
import gui.widget.AdminUserItem;
import gui.widget.item.MinimalItemAdmin;
import gui.widget.item.MinimalUser;
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

    public void addNewUser(AdminUserItem userItem) {
        mainTilePane.getChildren().addFirst(userItem);
    }

    @FXML
    protected void initialize() {
        AuctionEventBus.addListener("FETCH_AUCTIONS_SUCCESS", event -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data =
                    (List<Map<String, Object>>) ((NetworkMessage) event.getNewValue()).getData();
            Platform.runLater(() -> {
                mainTilePane.getChildren().clear();
                data.forEach(map -> addNewAuction(buildMinimalItem(map)));
            });
        });

        AuctionEventBus.addListener("FETCH_USERS_SUCCESS", event -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> users =
                    (List<Map<String, Object>>) ((NetworkMessage) event.getNewValue()).getData();
            Platform.runLater(() -> {
                // FIX LỖI 1: Đổi deleteAllAuctions() → deleteAllAuction()
                // (tên method trong lớp cha TableController là deleteAllAuction — không có 's')
                // HOẶC: Đổi tên trong TableController thành deleteAllAuctions() cho nhất quán
                deleteAllAuction();  // ✅ FIX: gọi đúng tên method của lớp cha
                users.forEach(data -> mainTilePane.getChildren().add(buildUserItem(data)));
            });
        });
    }

    private MinimalUser buildUserItem(Map<String, Object> data) {
        String  id        = (String)  data.get("id");
        String  username  = (String)  data.get("username");
        String  name      = (String)  data.get("name");
        String  role      = (String)  data.get("role");
        boolean isBlocked = (boolean) data.get("is_blocked");

        MinimalUser userItem = new MinimalUser(id, username, name, role, isBlocked);
        userItem.setCommand(command -> {
            if ("BLOCK_USER".equals(command)) {
                AdminService.blockUser(id);
            } else if ("UNBLOCK_USER".equals(command)) {
                AdminService.unblockUser(id);
            }
        });
        return userItem;
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
        String id   = (String) map.get("id");
        String name = (String) map.get("itemName");

        MinimalItemAdmin item = new MinimalItemAdmin(id, name, "");
        item.addAdminOptions(id, command -> {
            switch (command) {
                case "APPROVE_AUCTION" -> AdminService.approveAuction(id);
                case "REJECT_AUCTION"  -> AdminService.rejectAuction(id);
            }
        });
        return item;
    }
}
