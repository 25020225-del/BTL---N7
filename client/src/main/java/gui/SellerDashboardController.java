package gui;

import gui.process.AlertHelper;
import gui.process.CropImage;
import gui.process.ImageCompressor;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import model.auction.Auction;
import model.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static utils.ConsoleColors.GREEN;
import static utils.ConsoleColors.RESET;
import static utils.ConsoleColors.YELLOW;

/**
 * Controller dedicated to Seller-specific operations.
 * Manages auction creation and seller dashboard logic.
 */
public class SellerDashboardController {
    private static final Logger log = LoggerFactory.getLogger(SellerDashboardController.class);

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

    private File imagefile;

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

    public void setImageFile(File file) {
        this.imagefile = file;
        if (file != null) {
            Image image = new Image(file.toURI().toString());
            CropImage.cropImage(ca_image, image, 720, 480);
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
