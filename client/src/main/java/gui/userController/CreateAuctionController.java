package gui.userController;

import client.handler.AuctionEventBus;
import client.service.AuctionService;
import gui.MainApplication;
import gui.process.AlertUtils;
import gui.process.CreateAuctionModel;
import gui.process.CropImage;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import model.auction.Auction;
import model.item.Item;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Controller cho màn hình tạo phiên đấu giá mới.
 *
 * <h3>Refactor so với phiên bản cũ:</h3>
 * <ul>
 *   <li><b>Vấn đề gốc:</b> Validation errors chỉ được ghi bởi {@code logger.error()}.
 *       Người dùng không thấy bất kỳ phản hồi nào trên UI, tưởng phần mềm bị treo.</li>
 *   <li><b>Giải pháp:</b> Mọi lỗi validation giờ đây được hiển thị trực tiếp trên UI
 *       thông qua {@link AlertUtils}.</li>
 * </ul>
 *
 * <h3>Hai cách tiếp cận được triển khai:</h3>
 * <ul>
 *   <li><b>Cách 1 — Alert Popup:</b> {@link #handleSubmitAuction()} dùng
 *       {@code AlertUtils.showError()} khi bắt Exception từ validation.</li>
 * </ul>
 */
public class CreateAuctionController extends ScrollPane {

    private static final Logger log = LoggerFactory.getLogger(CreateAuctionController.class);

    private Runnable onAuctionCreated;
    private File imagefile;

    // ── Form fields ───────────────────────────────────────────────────────────
    @FXML private TextField ca_itemName;
    @FXML private TextArea  ca_description;
    @FXML private TextField ca_startPrice;
    @FXML private TextField ca_bidIncrement;
    @FXML private DatePicker ca_startDate;
    @FXML private TextField ca_startHour;
    @FXML private TextField ca_startMinute;
    @FXML private TextField ca_durationDays;
    @FXML private TextField ca_durationHours;
    @FXML private ImageView ca_image;

    /**
     * [CHỈ DÙNG CHO CÁCH 2] Label lỗi inline nằm cuối form.
     *
     * <p>Thêm vào CreateAuction.fxml (ẩn mặc định):</p>
     * <pre>{@code
     * <Label fx:id="lblError"
     *        visible="false"
     *        managed="false"
     *        wrapText="true"
     *        maxWidth="400"
     *        style="-fx-text-fill: #D32F2F;"/>
     * }</pre>
     */
    @FXML private Label lblError; // nullable — chỉ bắt buộc khi dùng Cách 2

    // ── Constructor ───────────────────────────────────────────────────────────

    public CreateAuctionController() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/CreateAuction.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Không thể tải CreateAuction.fxml", e);
        }
    }

    public void setOnAuctionCreated(Runnable callback) {
        this.onAuctionCreated = callback;
    }

    // ── FXML Handlers ─────────────────────────────────────────────────────────

    /**
     * Xử lý chọn ảnh từ máy tính.
     */
    @FXML
    private void handleSelectImage() {
        try {
            imagefile = CreateAuctionModel.selectImageFile();
            if (imagefile != null) {
                log.info("Selected image file: {}", imagefile.getName());
                Image image = new Image(imagefile.toURI().toString());
                CropImage.cropImage(ca_image, image, 720, 480);
            }
            // null = người dùng bấm Cancel — không cần thông báo
        } catch (IllegalArgumentException e) {
            // Ảnh quá nặng (> 10MB) — hiện popup lỗi
            log.warn("Image rejected: {}", e.getMessage());
            AlertUtils.showError("Lỗi Dung Lượng Ảnh", e.getMessage());
        }
    }

    // =========================================================================
    // CÁCH 1: ALERT POPUP — handleSubmitAuction()
    // =========================================================================
    // Ưu điểm: Không cần sửa FXML, cài đặt nhanh.
    // Nhược điểm: Popup ngắt luồng, người dùng phải bấm OK để đóng.
    // Phù hợp: Lỗi nghiêm trọng, lỗi server, lỗi không phải form validation.
    // =========================================================================

    /**
     * [CÁCH 1] Xử lý submit form — hiển thị lỗi qua Alert Popup.
     *
     * <p>Map exception → thông báo người dùng:</p>
     * <ul>
     *   <li>{@code IllegalArgumentException}: Lỗi validation (trường trống, giá sai)</li>
     *   <li>{@code NumberFormatException}: Người dùng nhập ký tự không phải số</li>
     *   <li>{@code DateTimeException}: Giờ/phút nhập sai dải hợp lệ</li>
     *   <li>{@code Exception} khác: Lỗi không xác định — log + báo user</li>
     * </ul>
     */
    @FXML
    public void handleSubmitAuction() {
        String name       = ca_itemName.getText().trim();
        String desc       = ca_description.getText().trim();
        String startPrice = ca_startPrice.getText().trim();
        String bidInc     = ca_bidIncrement.getText().trim();

        try {
            CreateAuctionModel.checkInputInfo(name, desc, startPrice, bidInc, imagefile);

            LocalDateTime startDT = CreateAuctionModel.checkStartTime(
                    ca_startDate.getValue(),
                    ca_startHour.getText(),
                    ca_startMinute.getText()
            );
            Duration dr = CreateAuctionModel.checkEndTime(
                    ca_durationDays.getText(),
                    ca_durationHours.getText()
            );

            Item item = CreateAuctionModel.createItem(
                    name, desc, Long.parseLong(startPrice), imagefile
            );
            Auction auction = CreateAuctionModel.createAuction(
                    item, new User(), Long.parseLong(bidInc), startDT, startDT.plus(dr)
            );

            AuctionService.createAuction(auction);

        } catch (NumberFormatException e) {
            AlertUtils.showError("Định Dạng Không Hợp Lệ",
                    "Vui lòng kiểm tra lại:\n"
                            + "• Giá tiền, bước giá phải là số nguyên\n"
                            + "• Ngày/Giờ diễn ra và Thời lượng không được để trống");

        } catch (IllegalArgumentException e) {
            // Validation lỗi — hiện thông báo rõ ràng cho người dùng
            log.warn("Auction creation validation failed: {}", e.getMessage());
            AlertUtils.showError("Lỗi Dữ Liệu", e.getMessage());



        } catch (java.time.DateTimeException e) {
            log.warn("Invalid date/time input: {}", e.getMessage());
            AlertUtils.showError("Thời Gian Không Hợp Lệ",
                    "Giờ bắt đầu phải từ 0–23 và phút từ 0–59.");

        } catch (Exception e) {
            // Lỗi không mong đợi — vẫn phải báo user, đừng nuốt exception!
            log.error("Unexpected error creating auction", e);
            AlertUtils.showError("Lỗi Hệ Thống",
                    "Đã xảy ra lỗi không mong đợi. Vui lòng thử lại hoặc liên hệ hỗ trợ.");
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @FXML
    private void initialize() {
        AuctionEventBus.addListener(AuctionEventBus.AUCTION_CREATED, event ->
                Platform.runLater(() -> {
                    resetForm();
                    if (onAuctionCreated != null) {
                        onAuctionCreated.run();
                    }
                })
        );
    }

    private void resetForm() {
        ca_itemName.clear();
        ca_description.clear();
        ca_startPrice.clear();
        ca_bidIncrement.clear();
        ca_startHour.clear();
        ca_startMinute.clear();
        ca_startDate.setValue(null);
        ca_durationDays.clear();
        ca_durationHours.clear();
        ca_image.setImage(null);
        imagefile = null;
        AlertUtils.clearInlineError(lblError);
    }
}