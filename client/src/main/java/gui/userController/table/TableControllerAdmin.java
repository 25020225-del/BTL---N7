package gui.userController.table;

import client.handler.AuctionEventBus;
import client.service.AdminService;
import gui.process.AlertUtils;
import gui.widget.AdminUserItem;
import gui.widget.item.MinimalItemAdmin;
import gui.widget.item.MinimalUser;
import gui.widget.item.WithdrawRequestItem;
import javafx.application.Platform;
import javafx.fxml.FXML;
import model.auction.Auction;
import network.NetworkMessage;

import java.util.List;
import java.util.Map;

/**
 * Authoritative system grid view controller. Specializes in handling infrastructure events,
 * administrative data structures mapping, and financial ledger settlement interactions.
 */
public class TableControllerAdmin extends TableController {

    public void addNewUser(AdminUserItem userItem) {
        mainTilePane.getChildren().addFirst(userItem);
    }

    /**
     * Binds specialized enterprise telemetry listeners to coordinate safe,
     * cross-thread UI mutations based on administrative incoming frames.
     */
    @FXML
    protected void initialize() {
        AuctionEventBus.addListener(AuctionEventBus.FETCH_AUCTIONS_SUCCESS, event -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data =
                    (List<Map<String, Object>>) ((NetworkMessage) event.getNewValue()).getData();
            Platform.runLater(() -> {
                mainTilePane.getChildren().clear();
                data.forEach(map -> addNewItem(buildMinimalItem(map)));
            });
        });

        AuctionEventBus.addListener(AuctionEventBus.FETCH_USERS_SUCCESS, event -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> users =
                    (List<Map<String, Object>>) ((NetworkMessage) event.getNewValue()).getData();
            Platform.runLater(() -> {
                deleteAllItem();
                users.forEach(data -> mainTilePane.getChildren().add(buildUserItem(data)));
            });
        });

        AuctionEventBus.addListener("FETCH_WITHDRAW_REQUESTS_SUCCESS", event -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> requests =
                    (List<Map<String, Object>>) ((NetworkMessage) event.getNewValue()).getData();

            Platform.runLater(() -> {
                deleteAllItem();
                for (Map<String, Object> req : requests) {
                    mainTilePane.getChildren().add(
                            new WithdrawRequestItem(req, command -> {
                                String[] parts = command.split(":", 2);
                                String action = parts[0];
                                String id = parts[1];
                                if ("APPROVE".equals(action)) {
                                    AdminService.approveWithdraw(id);
                                } else {
                                    AdminService.rejectWithdraw(id);
                                }
                            })
                    );
                }
            });
        });

        AuctionEventBus.addListener("WITHDRAW_ACTION_SUCCESS", event -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> result =
                    (Map<String, Object>) ((NetworkMessage) event.getNewValue()).getData();
            String msg = (String) result.get("message");

            Platform.runLater(() -> {
                AlertUtils.showInfo("Success", msg);
                AdminService.fetchWithdrawRequests();
            });
        });
    }

    private MinimalUser buildUserItem(Map<String, Object> data) {
        String id = (String) data.get("id");
        String username = (String) data.get("username");
        String name = (String) data.get("name");
        String role = (String) data.get("role");
        boolean isBlocked = (boolean) data.get("is_blocked");
        boolean isGood = (boolean) data.get("is_good");

        MinimalUser userItem = new MinimalUser(id, username, name, role, isBlocked, isGood);
        userItem.setCommand(command -> {
            switch (command) {
                case "BLOCK_USER" -> AdminService.blockUser(id);
                case "UNBLOCK_USER" -> AdminService.unblockUser(id);
                case "TOGGLE_GOOD_STATUS" -> AdminService.toggleGoodStatus(id);
            }
        });
        return userItem;
    }

    protected MinimalItemAdmin buildMinimalItem(Map<String, Object> map) {
        String id = (String) map.get("id");
        String name = (String) map.get("itemName");
        String status = (String) map.get("status");

        Object currentPriceVal = map.get("currentPrice");
        long price = currentPriceVal instanceof Number ? ((Number) currentPriceVal).longValue() : 0L;

        Auction auction = Auction.buildAuctionFromMap(map);

        MinimalItemAdmin item = new MinimalItemAdmin(id, name, status, price);
        item.addAdminOptions(id, command -> {
            switch (command) {
                case "APPROVE_AUCTION" -> AdminService.approveAuction(id);
                case "REJECT_AUCTION" -> AdminService.rejectAuction(id);
                case "SHOW_AUCTION" -> openItemDetail(auction);
                case "CANCEL_AUCTION" -> AdminService.cancelAuction(id);
            }
        });
        return item;
    }
}