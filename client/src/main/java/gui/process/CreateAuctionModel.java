package gui.process;

import gui.MainApplication;
import javafx.stage.FileChooser;
import model.auction.Auction;
import model.item.Item;
import model.item.ItemFactory;
import model.user.User;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Core transactional model controller backing the Auction Creation layout form wizard.
 * Handles pure data structure parsing, file boundary constraints, and logic mutations.
 * Enforces loose UI decoupling by bubbling up validation constraints via Exceptions.
 */
public class CreateAuctionModel {

    public static final int MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10 MB

    /**
     * Mở FileChooser để người dùng chọn ảnh.
     *
     * @return File đã chọn, hoặc {@code null} nếu hủy.
     * @throws IllegalArgumentException nếu ảnh vượt quá 10MB.
     */
    public static File selectImageFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh sản phẩm");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );

        File selectedFile = fileChooser.showOpenDialog(
                MainApplication.primalStage.getScene().getWindow()
        );

        if (selectedFile == null) {
            return null; // Người dùng bấm Cancel
        }

        // REFACTOR: Ném exception thay vì gọi AlertHelper trực tiếp.
        // Controller sẽ bắt và hiển thị lỗi qua AlertUtils.
        if (selectedFile.length() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Ảnh quá nặng. Vui lòng chọn ảnh có dung lượng nhỏ hơn 10MB."
            );
        }
        return selectedFile;
    }

    /**
     * Kiểm tra các trường nhập liệu bắt buộc.
     *
     * @throws IllegalArgumentException với message mô tả lỗi cụ thể nếu validation thất bại.
     */
    public static void checkInputInfo(
            String name, String desc, String startPrice, String bidInc, File image
    ) {
        // Kiểm tra trường trống
        if (name.isEmpty() || desc.isEmpty() || startPrice.isEmpty() || bidInc.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng điền đầy đủ các trường bắt buộc.");
        }
        if (image == null) {
            throw new IllegalArgumentException("Vui lòng chọn ảnh cho sản phẩm.");
        }

        // Kiểm tra giá tối thiểu — FIX: "Vui đòng" → "Vui lòng"
        long price = Long.parseLong(startPrice); // NumberFormatException nếu không hợp lệ
        if (price < 2000) {
            throw new IllegalArgumentException(
                    "Vui lòng đặt mức giá khởi điểm tối thiểu 2.000 VNĐ."
            );
        }

        long increment = Long.parseLong(bidInc);
        if (increment < 1000) {
            // FIX LỖI CHÍNH TẢ GỐC: "Vui đòng" → "Vui lòng"
            throw new IllegalArgumentException(
                    "Vui lòng đặt bước giá tối thiểu 1.000 VNĐ."
            );
        }
    }

    /**
     * Kiểm tra và tạo LocalDateTime cho thời điểm bắt đầu đấu giá.
     *
     * @throws NumberFormatException       nếu giờ/phút không hợp lệ.
     * @throws java.time.DateTimeException nếu giá trị giờ/phút nằm ngoài phạm vi.
     */
    public static LocalDateTime checkStartTime(
            LocalDate date, String startHour, String startMinute
    ) {
        if (date == null || startHour == null || startMinute == null
                || startHour.isEmpty() || startMinute.isEmpty()) {
            throw new NumberFormatException("Vui lòng chọn ngày và giờ bắt đầu.");
        }
        int hour = Integer.parseInt(startHour);
        int minute = Integer.parseInt(startMinute);
        return date.atTime(hour, minute); // DateTimeException nếu hour/minute sai range
    }

    /**
     * Kiểm tra và tạo Duration cho thời lượng đấu giá.
     *
     * @throws NumberFormatException    nếu số ngày/giờ không hợp lệ.
     * @throws IllegalArgumentException nếu tổng thời lượng bằng 0.
     */
    public static Duration checkEndTime(String days, String hours) {
        if (days == null || hours == null || days.trim().isEmpty() || hours.trim().isEmpty()) {
            throw new NumberFormatException("Vui lòng nhập thời lượng đấu giá.");
        }
        int d = Integer.parseInt(days.trim());
        int h = Integer.parseInt(hours.trim());
        Duration duration = Duration.ofDays(d).plusHours(h);
        if (duration.isZero()) {
            throw new IllegalArgumentException("Thời lượng đấu giá phải lớn hơn 0.");
        }
        return duration;
    }

    /**
     * Tạo Item từ dữ liệu form và file ảnh.
     */
    public static Item createItem(String name, String type, String desc, long startPrice, File image)
            throws Exception {
        String itemId = "ITEM-" + utils.IdGenerator.generateUUIDv7();
        Item item = ItemFactory.createItem(type, itemId, name, desc, startPrice);
        byte[] imageBytes = ImageCompressor.compressToBytes(image, 0.05F);
        item.setFile(imageBytes);
        return item;
    }

    /**
     * Tạo Auction từ Item và các thông số đấu giá.
     */
    public static Auction createAuction(
            Item item, User user, long bidInc,
            LocalDateTime startDateTime, LocalDateTime endDateTime
    ) {
        String auctionId = "AUC-" + utils.IdGenerator.generateUUIDv7();
        return new Auction(auctionId, item, user, bidInc, startDateTime, endDateTime);
    }
}