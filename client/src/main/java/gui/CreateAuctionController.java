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


public class CreateAuctionController {
    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);
    Parent createAuctionView;

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
    private Runnable onAuctionCreated;
    public CreateAuctionController(){

        FXMLLoader sellerLoader = new FXMLLoader(getClass().getResource("CreateAuction.fxml"));
        sellerLoader.setController(this);
        try {
            createAuctionView = sellerLoader.load();
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

        File selectedFile = fileChooser.showOpenDialog(createAuctionView.getScene().getWindow());

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

    public Parent getParent(){
        return createAuctionView;
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
            long durationMinutes = (days * 24L * 60L) + (hours * 60L);

            if (durationMinutes <= 0 || durationMinutes > 43200) {
                AlertHelper.showAlert(Alert.AlertType.WARNING, "Invalid Duration", "Thời lượng đấu giá phải từ 1 phút đến tối đa 30 ngày.");
                return;
            }

            Auction auction = new Auction();
            auction.setItem(new Item());
            auction.getItem().setItemName(name);
            auction.getItem().setDescription(desc);
            auction.getItem().setStartingPrice(Double.parseDouble(startPrice));
            auction.getItem().setFile(ImageCompressor.compressToBytes(imagefile, 0.05F));
            auction.setBidIncrement(Double.parseDouble(bidInc));
            auction.setEndTime(startDT.plusMinutes(durationMinutes));
            auction.setStartTime(startDT);

            MainApplication.networkClient.sendMessage("CREATE_AUCTION", auction);

            resetForm();

        } catch (NumberFormatException e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Format Error", "Giá tiền và thời lượng phải là số hợp lệ.");
        } catch (java.time.DateTimeException e) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, "Time Error", "Giờ bắt đầu phải từ 0–23 và phút từ 0–59.");
        } catch (Exception e) {
            e.printStackTrace();
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