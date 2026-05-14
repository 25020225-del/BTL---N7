package gui.model;


import gui.MainApplication;
import gui.MainController;
import gui.process.AlertHelper;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;

import java.io.File;

public class CreateAuctionModel {
    public static final int MAX_IMAGE_SIZE = 10 * 1024 * 1024;
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
    public static void validate(String name, String desc, String startPrice, String bidInc, File image) throws Exception {
        if (name.isEmpty() || desc.isEmpty() || startPrice.isEmpty() || bidInc.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng điền đầy đủ các trường bắt buộc.");
        }
        if (image == null) {
            throw new IllegalArgumentException("Vui lòng chọn ảnh cho sản phẩm.");
        }
    }

}
