package server.handler;

import exception.AuctionExceptions;
import network.ErrorPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.auction.Auction;
import model.item.Item;
import model.item.ItemFactory;
import model.user.User;
import network.NetworkMessage;
import server.ClientHandler;
import server.ServerExtension.AuctionManager;
import server.ServerExtension.ClientManager;
import service.CloudinaryService;
import utils.JacksonConfig;

import java.time.LocalDateTime;

import static utils.ConsoleColors.*;

public class AuctionActionHandler implements CommandHandler {
    private static final Logger log = LoggerFactory.getLogger(AuctionActionHandler.class);
    private final ObjectMapper mapper = JacksonConfig.mapper();
    private final controller.ServerSellerController sellerCtrl;

    public AuctionActionHandler(controller.ServerSellerController sellerCtrl) {
        this.sellerCtrl = sellerCtrl;
    }

    @Override
    public void handle(NetworkMessage message, ClientHandler client) throws Exception {
        if ("CREATE_AUCTION".equals(message.getCommand())) {
            processCreateAuction(message.getData(), client);
        } else {
            throw new AuctionExceptions.InvalidPayloadException("Lệnh tạo phiên đấu giá không hợp lệ.");
        }
    }

    private void processCreateAuction(Object data, ClientHandler client) throws Exception {
        User authenticatedUser = client.getUser();
        if (authenticatedUser == null) {
            throw new AuctionExceptions.UnauthorizedAccessException("Bạn cần đăng nhập để tạo phiên đấu giá.");
        }

        Auction auction;
        try {
            auction = mapper.convertValue(data, new com.fasterxml.jackson.core.type.TypeReference<Auction>() {});
        } catch (IllegalArgumentException e) {
            throw new AuctionExceptions.InvalidPayloadException("Dữ liệu phiên đấu giá không hợp lệ.");
        }

        String itemName = auction.getItem().getItemName();
        String description = auction.getItem().getDescription();
        String imageUrl = CloudinaryService.uploadImage(auction.getItem().getFile());
        long startingPrice = auction.getItem().getStartingPrice();
        long bidIncrement = auction.getBidIncrement();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reqStart = auction.getStartTime();
        LocalDateTime reqEnd = auction.getEndTime();

        if (reqStart == null || reqEnd == null) {
            throw new AuctionExceptions.InvalidPayloadException("Định dạng thời gian không hợp lệ.");
        }

        if (reqStart.isBefore(now.minusMinutes(5))) {
            throw new AuctionExceptions.InvalidPayloadException("Thời gian bắt đầu không được nằm trong quá khứ.");
        }

        if (reqStart.isBefore(now)) {
            reqStart = now;
        }

        long durationMinutes = java.time.Duration.between(reqStart, reqEnd).toMinutes();
        final long MAX_DURATION_MINUTES = 43200; // 30 days

        if (durationMinutes <= 0) {
            throw new AuctionExceptions.InvalidPayloadException("Thời lượng đấu giá không hợp lệ (phải lớn hơn 0).");
        }
        if (durationMinutes > MAX_DURATION_MINUTES) {
            durationMinutes = MAX_DURATION_MINUTES;
        }

        String itemType = auction.getItem().getType() != null
                ? auction.getItem().getType()
                : ItemFactory.TYPE_TANGIBLE;
        String newItemId = "ITM-" + System.currentTimeMillis();
        Item item = ItemFactory.createItem(itemType, newItemId, itemName, description, startingPrice);
        item.setImageUrl(imageUrl);

            // Forward the creation request to the Seller Controller
            model.user.Seller sellerRole = new model.user.Seller(authenticatedUser);
            Auction newAuction = sellerCtrl.addAuction(sellerRole, item, bidIncrement, reqStart, (int) durationMinutes);

        if (newAuction != null) {
            log.info("{} has created an auction.", authenticatedUser.getUserName());
            if (authenticatedUser.isGood()) {
                newAuction.setStatus(Auction.STATUS_RUNNING);
                AuctionManager.addAuctionToMonitor(newAuction);

                String alertMsg = "[System]: Seller \"" + authenticatedUser.getName() + "\" has created an auction for \"" + YELLOW + itemName + RESET + "\" - " + GREEN + startingPrice + RESET + " VND";
                ClientManager.broadcast("CLI_BROADCAST", alertMsg, client);

                client.sendResponse("CREATE_SUCCESS", "Tạo phiên đấu giá thành công.");
                ClientManager.broadcast("NEW_AUCTION_ADDED", newAuction, null);
            } else {
                client.sendResponse("CREATE_SUCCESS", "Đã tạo phiên đấu giá, đang chờ Quản trị viên duyệt.");
            }
        } else {
            client.sendResponse("ERROR", new ErrorPayload("ERR_DB_005", "Không thể tạo phiên đấu giá do lỗi cơ sở dữ liệu."));
        }
    }
}