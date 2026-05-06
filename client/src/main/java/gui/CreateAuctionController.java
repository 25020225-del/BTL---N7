package gui;

import gui.process.AlertHelper;
import gui.process.CropImage;
import gui.process.ImageCompressor;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import model.auction.Auction;
import model.item.Item;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CreateAuctionController extends javafx.scene.control.ScrollPane {
    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    private Runnable onAuctionCreated;

    private File imagefile;

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
        FXMLLoader loader = new FXMLLoader(getClass().getResource("CreateAuction.fxml"));
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

    /**
     * Choose image from local storage.
     */
    @FXML
    private void handleSelectImage(){
        // Hạ giới hạn xuống 1MB để bảo vệ RAM của Server khi mã hóa và parse JSON
        final int MAX_IMAGE_SIZE = 1 * 1024 * 1024;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose an image");
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File selectedFile = fileChooser.showOpenDialog(this.getScene().getWindow());

        if (selectedFile != null) {
            // Kiểm tra dung lượng NGAY LẬP TỨC trước khi làm bất cứ việc gì
            if (selectedFile.length() > MAX_IMAGE_SIZE) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, "Lỗi dung lượng", "Ảnh quá nặng, đề nghị chọn ảnh có dung lượng nhỏ hơn 1MB để đảm bảo đường truyền mạng!");
                return; // Thoát ngay, không lưu file này
            }

            // Nếu qua được vòng kiểm duyệt thì mới gán vào biến toàn cục và hiển thị lên UI
            this.imagefile = selectedFile;
            log.info("Selected image file: {}", imagefile.getName());

            Image image = new Image(imagefile.toURI().toString());
            CropImage.cropImage(ca_image, image, 720, 480);
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

            if (name.isEmpty() || desc.isEmpty() || startPrice.isEmpty() || bidInc.isEmpty()) {
                AlertHelper.showAlert(Alert.AlertType.WARNING, "Missing Fields", "Please fill in all required fields.");
                return;
            }
            if (imagefile == null) {
                AlertHelper.showAlert(Alert.AlertType.WARNING, "Missing Image", "Please select an image file.");
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startDT;

            boolean isStartTimeEmpty = ca_startDate.getValue() == null ||
                    ca_startHour.getText().trim().isEmpty() ||
                    ca_startMinute.getText().trim().isEmpty();

            if (isStartTimeEmpty) {
                startDT = now;
            } else {
                startDT = LocalDateTime.of(
                        ca_startDate.getValue(),
                        LocalTime.of(Integer.parseInt(ca_startHour.getText().trim()), Integer.parseInt(ca_startMinute.getText().trim()))
                );

                if (startDT.isBefore(now.plusDays(1))) {
                    AlertHelper.showAlert(Alert.AlertType.WARNING, "Invalid Time", "Thời gian bắt đầu phải để trống hoặc phải cách hiện tại ít nhất 1 ngày (24 giờ).");
                    return;
                }
            }

            int days = ca_durationDays.getText().trim().isEmpty() ? 0 : Integer.parseInt(ca_durationDays.getText().trim());
            int hours = ca_durationHours.getText().trim().isEmpty() ? 0 : Integer.parseInt(ca_durationHours.getText().trim());
            int totalDurationMinutes = (days * 24 * 60) + (hours * 60);

            if (totalDurationMinutes <= 0 || totalDurationMinutes > 43200) {
                AlertHelper.showAlert(Alert.AlertType.WARNING, "Invalid Duration", "Thời lượng đấu giá phải từ 1 phút đến tối đa 30 ngày.");
                return;
            }

            // Image size and compression check
            byte[] imageBytes = ImageCompressor.compressToBytes(imagefile, 0.05F);
            if (imageBytes.length > 500 * 1024) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, "Image Too Large", "Ảnh sau khi nén vẫn lớn hơn 500KB. Vui lòng chọn ảnh khác hoặc liên hệ kỹ thuật để tăng độ nén!");
                return;
            }

            // Standardized Model Initialization using Parametrized Constructors
            String itemId = "ITEM-" + System.currentTimeMillis();
            Item item = new Item(itemId, name, desc, Long.parseLong(startPrice));
            item.setFile(imageBytes);

            String auctionId = "AUC-" + System.currentTimeMillis();
            // Assuming current user context is available or needs to be passed. 
            // For now, we use a placeholder or assume the server fills the User object correctly upon receipt.
            Auction auction = new Auction(auctionId, item, new model.user.User(), Long.parseLong(bidInc), startDT, startDT.plusMinutes(totalDurationMinutes));

            MainApplication.networkClient.sendMessage("CREATE_AUCTION", auction);

            resetForm();

        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Format Error", "Giá tiền và thời lượng phải là số hợp lệ.");
        } catch (java.time.DateTimeException e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Time Error", "Giờ bắt đầu phải từ 0–23 và phút từ 0–59.");
        } catch (Exception e) {
            log.error(e.getMessage());
        }
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