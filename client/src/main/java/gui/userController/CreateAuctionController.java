package gui.userController;

import client.handler.AuctionEventBus;
import client.service.AuctionService;
import gui.process.AlertUtils;
import gui.process.CreateAuctionModel;
import gui.process.CropImage;
import gui.widget.Selector;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import model.auction.Auction;
import model.item.Item;
import model.item.ItemFactory;
import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Presentation controller managing the asset creation interface lifecycle.
 * Coordinates inbound view form validation constraints and translates input metrics
 * into persistent domain entity models via isolated validation bridge services.
 */
public class CreateAuctionController extends ScrollPane {

    private static final Logger log = LoggerFactory.getLogger(CreateAuctionController.class);
    private PropertyChangeListener auctionCreatedListener;

    private Runnable onAuctionCreated;
    private File imagefile;
    private User currentUser;

    @FXML private TextField ca_itemName;
    @FXML private TextArea ca_description;
    @FXML private TextField ca_startPrice;
    @FXML private TextField ca_bidIncrement;
    @FXML private DatePicker ca_startDate;
    @FXML private TextField ca_startHour;
    @FXML private TextField ca_startMinute;
    @FXML private TextField ca_durationDays;
    @FXML private TextField ca_durationHours;
    @FXML private ImageView ca_image;
    @FXML private VBox ca_details;
    @FXML private Label lblError;

    private Selector selector;

    /**
     * Loads the graphical layout tree hierarchy and establishes this instance as the contextual root handler.
     */
    public CreateAuctionController(User currentUser) {
        this.currentUser = currentUser; // Gán giá trị người dùng được truyền vào

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/CreateAuction.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Fatal error loading asset compilation FXML visual layout descriptor", e);
        }
    }

    public void setOnAuctionCreated(Runnable callback) {
        this.onAuctionCreated = callback;
    }

    @FXML
    private void handleSelectImage() {
        try {
            Window ownerWindow = ca_image.getScene().getWindow();
            imagefile = CreateAuctionModel.selectImageFile(ownerWindow);

            if (imagefile != null) {
                log.info("Selected image file mapped directly to canvas bounds: {}", imagefile.getName());
                Image image = new Image(imagefile.toURI().toString());
                CropImage.cropImage(ca_image, image, 720, 480);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Image file constraint violation intercepted: {}", e.getMessage());
            AlertUtils.showError("Lỗi Dung Lượng Ảnh", e.getMessage());
        }
    }

    /**
     * Intercepts, extracts, and orchestrates formal transformation logic over structural form attributes.
     * Evaluates boundary constraints and handles localized failure propagation parameters.
     */
    @FXML
    public void handleSubmitAuction() {
        String name = ca_itemName.getText().trim();
        String desc = ca_description.getText().trim();
        String startPrice = ca_startPrice.getText().trim();
        String bidInc = ca_bidIncrement.getText().trim();

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
                    name, selector.getChoice(), desc, Long.parseLong(startPrice), imagefile
            );

            Auction auction = CreateAuctionModel.createAuction(
                    item, this.currentUser, Long.parseLong(bidInc), startDT, startDT.plus(dr)
            );
            AuctionService.createAuction(auction);

        } catch (NumberFormatException e) {
            AlertUtils.showError("Định Dạng Không Hợp Lệ",
                    "Vui lòng kiểm tra lại:\n"
                            + "• Giá tiền, bước giá phải là số nguyên\n"
                            + "• Ngày/Giờ diễn ra và Thời lượng không được để trống");

        } catch (IllegalArgumentException e) {
            log.warn("Form mapping parameter structure validation failed: {}", e.getMessage());
            AlertUtils.showError("Lỗi Dữ Liệu", e.getMessage());

        } catch (java.time.DateTimeException e) {
            log.warn("Temporal alignment constraint failed index mappings: {}", e.getMessage());
            AlertUtils.showError("Thời Gian Không Hợp Lệ", "Giờ bắt đầu phải từ 0–23 và phút từ 0–59.");

        } catch (Exception e) {
            log.error("Unhandled runtime boundary breakdown during auction compilation sequence", e);
            AlertUtils.showError("Lỗi Hệ Thống", "Đã xảy ra lỗi không mong đợi. Vui lòng thử lại.");
        }
    }

    @FXML
    private void initialize() {
        selector = new Selector("type", ItemFactory.TYPE_TANGIBLE, ItemFactory.TYPE_DIGITAL, ItemFactory.TYPE_SERVICE);
        ca_details.getChildren().add(selector);

        auctionCreatedListener = event ->
                Platform.runLater(() -> {
                    resetForm();
                    if (onAuctionCreated != null) onAuctionCreated.run();
                });
        AuctionEventBus.addListener(AuctionEventBus.AUCTION_CREATED, auctionCreatedListener);
    }
    public void dispose() {
        AuctionEventBus.removeListener(AuctionEventBus.AUCTION_CREATED, auctionCreatedListener);
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
        ca_image.setFitHeight(-1.0);
        ca_image.setFitWidth(-1.0);
        imagefile = null;
        AlertUtils.clearInlineError(lblError);
    }
}