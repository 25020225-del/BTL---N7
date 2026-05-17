package gui.process;


import gui.MainApplication;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import model.auction.Auction;
import model.item.Item;
import model.user.User;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class CreateAuctionProcess {
    public static void checkInputInfo(String name, String desc, String startPrice, String bidInc, File image) throws Exception {
        if (name.isEmpty() || desc.isEmpty() || startPrice.isEmpty() || bidInc.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng điền đầy đủ các trường bắt buộc.");
        }
        if (image == null) {
            throw new IllegalArgumentException("Vui lòng chọn ảnh cho sản phẩm.");
        }
        if (Long.parseLong(startPrice) < 2000) {
            throw new IllegalArgumentException("Vui lòng đặt hàng với mức giá tối thiểu 2000 vnd");
        }
        if (Long.parseLong(bidInc) < 1000) {
            throw new IllegalArgumentException("Vui đòng đặt hàng với bước giá tối thiểu 1000 vnd");
        }
    }
    public static LocalDateTime checkStartTime(LocalDate date, String startHour, String startMinute) throws Exception{
        if (date==null || startHour==null || startMinute==null) {
            throw new NumberFormatException();
        }
        if (startHour.isEmpty() || startMinute.isEmpty()) {
            throw new NumberFormatException();
        }
        int hour = Integer.parseInt(startHour);
        int minute = Integer.parseInt(startMinute);
        LocalDateTime startTime;
        startTime = date.atTime(hour, minute);
        return startTime;
    }
    public static Duration checkEndTime(String days, String hours) throws Exception{
        if (days ==null || hours==null) {
            throw new NumberFormatException();
        }
        if (hours.trim().isEmpty() || days.trim().isEmpty()) {
            throw new NumberFormatException();
        }
        Duration duration = Duration.ofDays(Integer.parseInt(days)).plusHours(Integer.parseInt(hours));
        return duration;
    }
    public static Item createItem(String name, String desc, long startPrice, File image) throws Exception {
        String itemId = "ITEM-" + UUID.randomUUID() + System.currentTimeMillis();
        Item item = new Item(itemId, name, desc, startPrice);
        byte[] imageBytes = ImageCompressor.compressToBytes(image, 0.05F);
        item.setFile(imageBytes);
        return item;
    }
    public static Auction createAuction(Item item, User user, long bidInc, LocalDateTime startDateTime, LocalDateTime endDateTime) throws Exception {
        String auctionId = "AUC-" + UUID.randomUUID() + System.currentTimeMillis();
        Auction auction = new Auction(auctionId, item, new model.user.User(), bidInc, startDateTime,endDateTime);
        // Assuming current user context is available or needs to be passed.
        // For now, we use a placeholder or assume the server fills the User object correctly upon receipt.
        return auction;
    }
}
