package gui.userController;

import client.handler.AuctionEventBus;
import client.service.AuctionService;
import gui.MainApplication;
import gui.process.CreateAuctionProcess;
import gui.process.AlertHelper;
import gui.process.CropImage;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import model.auction.Auction;
import model.item.Item;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

import model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CreateAuctionController extends ScrollPane {
    private static final Logger log = LoggerFactory.getLogger(CreateAuctionController.class);

    private Runnable onAuctionCreated;

    private File imagefile;
    public static final int MAX_IMAGE_SIZE = 10 * 1024 * 1024;

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


    public CreateAuctionController(){
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/CreateAuction.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setOnAuctionCreated(Runnable callback) { // thêm method này
        this.onAuctionCreated = callback;
    }

    public static File selectImageFile() {

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose an image");
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File selectedFile = fileChooser.showOpenDialog(MainApplication.primalStage.getScene().getWindow());

        // Kiểm tra dung lượng NGAY LẬP TỨC trước khi làm bất cứ việc gì
        if (selectedFile.length() > MAX_IMAGE_SIZE) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Lỗi dung lượng", "Ảnh quá nặng, đề nghị chọn ảnh có dung lượng nhỏ hơn 10MB để đảm bảo đường truyền mạng!");
            return null;
        }
        return selectedFile;
    }
    /**
     * Choose image from local storage.
     */
    @FXML
    private void handleSelectImage(){
        imagefile = selectImageFile();
        if (imagefile != null) {
            log.info("Selected image file: {}", imagefile.getName());

            Image image = new Image(imagefile.toURI().toString());
            CropImage.cropImage(ca_image, image, 720, 480);
        }
        else {
            log.warn("Please select an image file");
        }
    }

    /**
     * Handles the submission of the Create Auction form.
     */
    @FXML
    public void handleSubmitAuction() {
        try {
            String name = ca_itemName.getText().trim();
            String desc = ca_description.getText().trim();
            String startPrice = ca_startPrice.getText().trim();
            String bidInc = ca_bidIncrement.getText().trim();
            LocalDateTime startDT;
            Duration dr;

            CreateAuctionProcess.checkInputInfo(name,desc,startPrice,bidInc,imagefile);
            startDT = CreateAuctionProcess.checkStartTime(ca_startDate.getValue(),ca_startHour.getText(),ca_startMinute.getText());
            dr = CreateAuctionProcess.checkEndTime(ca_durationDays.getText(),ca_durationHours.getText());

            Item item = CreateAuctionProcess.createItem(name, desc, Long.parseLong(startPrice), imagefile);

            Auction auction = CreateAuctionProcess.createAuction(item,new User(),Long.parseLong(bidInc),startDT,startDT.plus(dr));

            AuctionService.createAuction(auction);


        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Format Error", "Giá tiền và thời lượng phải là số hợp lệ.");
        } catch (java.time.DateTimeException e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Time Error", "Giờ bắt đầu phải từ 0–23 và phút từ 0–59.");
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    @FXML
    private void initialize() {
        AuctionEventBus.addListener(AuctionEventBus.AUCTION_CREATED,event -> {
            Platform.runLater(() -> {
                resetForm();
                if (onAuctionCreated != null) {
                    onAuctionCreated.run();
                }
            });
        });
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
    }
}