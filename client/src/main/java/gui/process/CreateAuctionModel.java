package gui.process;

import javafx.stage.FileChooser;
import javafx.stage.Window;
import model.auction.Auction;
import model.item.Item;
import model.item.ItemFactory;
import model.user.User;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Stateless validation and domain factory service supporting the auction creation wizard.
 * Enforces business rule constraints and asset parsing invariants independently of the
 * concrete UI event capture lifecycle.
 */
public class CreateAuctionModel {

    public static final int MAX_IMAGE_SIZE = 10 * 1024 * 1024;

    /**
     * Spawns a localized native file dialog context to resolve an image asset path.
     * Validates inbound binary payload boundaries prior to ingestion.
     *
     * @param ownerWindow the host window context managing the dialog overlay
     * @return the selected image file pointer, or {@code null} if the operation was aborted
     * @throws IllegalArgumentException if the selected asset profile scales past the 10MB threshold
     */
    public static File selectImageFile(Window ownerWindow) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh sản phẩm");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Ảnh", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );

        File selectedFile = fileChooser.showOpenDialog(ownerWindow);

        if (selectedFile == null) {
            return null;
        }

        if (selectedFile.length() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Ảnh quá nặng. Vui lòng chọn ảnh có dung lượng nhỏ hơn 10MB."
            );
        }
        return selectedFile;
    }

    /**
     * Enforces domain structural validation rules upon raw user form input collections.
     *
     * @param name       the descriptive nomenclature of the item
     * @param desc       the semantic details of the target asset
     * @param startPrice the starting monetary valuation text string
     * @param bidInc     the mandated incremental valuation stepping string
     * @param image      the binary handle representing the validated image file
     * @throws IllegalArgumentException if fields violate nullable constraints or fail minimum financial baselines
     */
    public static void checkInputInfo(
            String name, String desc, String startPrice, String bidInc, File image
    ) {
        if (name.isEmpty() || desc.isEmpty() || startPrice.isEmpty() || bidInc.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng điền đầy đủ các trường bắt buộc.");
        }
        if (image == null) {
            throw new IllegalArgumentException("Vui lòng chọn ảnh cho sản phẩm.");
        }

        long price = Long.parseLong(startPrice);
        if (price < 2000) {
            throw new IllegalArgumentException("Vui lòng đặt mức giá khởi điểm tối thiểu 2.000 VNĐ.");
        }

        long increment = Long.parseLong(bidInc);
        if (increment < 1000) {
            throw new IllegalArgumentException("Vui lòng đặt bước giá tối thiểu 1.000 VNĐ.");
        }
    }

    /**
     * Compiles a localized text representation of temporal values into a system datetime context.
     *
     * @param date        the target starting date matrix
     * @param startHour   the target hour indicator string
     * @param startMinute the target minute indicator string
     * @return an integrated chronological representation of the start sequence
     * @throws NumberFormatException if structural parsing of digital segments fails range constraints
     */
    public static LocalDateTime checkStartTime(LocalDate date, String startHour, String startMinute) {
        if (date == null || startHour == null || startMinute == null
                || startHour.isEmpty() || startMinute.isEmpty()) {
            throw new NumberFormatException("Vui lòng chọn ngày và giờ bắt đầu.");
        }
        int hour = Integer.parseInt(startHour);
        int minute = Integer.parseInt(startMinute);
        return date.atTime(hour, minute);
    }

    /**
     * Computes the absolute operational timespan duration metrics for the session lifecycle contract.
     *
     * @param days  the parameterized absolute day metric text
     * @param hours the parameterized absolute hour metric text
     * @return the verified structural duration configuration block
     * @throws IllegalArgumentException if the processed calculation yields zero temporal delta
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
     * Translates a raw validated file structure into an immutable system inventory item instance.
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
     * Assembles a verified concrete auction aggregate root ready for registration.
     */
    public static Auction createAuction(
            Item item, User user, long bidInc,
            LocalDateTime startDateTime, LocalDateTime endDateTime
    ) {
        String auctionId = "AUC-" + utils.IdGenerator.generateUUIDv7();
        return new Auction(auctionId, item, user, bidInc, startDateTime, endDateTime);
    }
}