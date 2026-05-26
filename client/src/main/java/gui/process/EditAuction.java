package gui.process;

import client.network.NetworkService;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import model.auction.Auction;

import java.util.HashMap;
import java.util.Map;

/**
 * Modal presentation wizard for mutating active auction metadata fields.
 * Encapsulates dynamic form generation, UI layout constraints, and client-side payload translation.
 */
public class EditAuction {

    /**
     * Spawns a modal transaction block allowing users to dispatch updated informational claims
     * regarding an active auction sequence.
     *
     * @param auction the target domain entity to edit
     */
    public static void edit(Auction auction) {
        Dialog<Map<String, Object>> dialog = new Dialog<>();
        dialog.setTitle("Chỉnh Sửa Phiên Đấu Giá");
        dialog.setHeaderText("Cập nhật thông tin cho phiên: " + auction.getId());

        ButtonType saveButtonType = new ButtonType("Lưu Thay Đổi", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 50, 10, 10));

        TextField nameField = new TextField(auction.getItem().getItemName());
        TextArea descField = new TextArea(auction.getItem().getDescription());
        descField.setPrefRowCount(3);
        TextField priceField = new TextField(String.valueOf(auction.getItem().getStartingPrice()));

        String startStr = auction.getStartTime() != null 
                ? auction.getStartTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) 
                : "";
        TextField startTimeField = new TextField(startStr);
        startTimeField.setPromptText("Định dạng: yyyy-MM-dd HH:mm:ss");

        TextField durationField = new TextField(String.valueOf(auction.getDurationMinutes()));
        durationField.setPromptText("Số phút (ví dụ: 60)");

        grid.add(new Label("Tên sản phẩm:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Mô tả chi tiết:"), 0, 1);
        grid.add(descField, 1, 1);
        grid.add(new Label("Giá khởi điểm (VND):"), 0, 2);
        grid.add(priceField, 1, 2);
        grid.add(new Label("Thời gian bắt đầu:"), 0, 3);
        grid.add(startTimeField, 1, 3);
        grid.add(new Label("Thời lượng (phút):"), 0, 4);
        grid.add(durationField, 1, 4);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                Map<String, Object> result = new HashMap<>();
                result.put("auctionId", auction.getId());
                result.put("itemName", nameField.getText());
                result.put("description", descField.getText());
                result.put("startPrice", priceField.getText());
                result.put("newStartTime", startTimeField.getText());
                result.put("durationMinutes", durationField.getText());
                return result;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(data -> {
            String name = ((String) data.get("itemName")).trim();
            String desc = ((String) data.get("description")).trim();
            String startPriceStr = ((String) data.get("startPrice")).trim();
            String startTimeStr = ((String) data.get("newStartTime")).trim();
            String durationStr = ((String) data.get("durationMinutes")).trim();

            if (name.isEmpty()) {
                AlertUtils.showError("Lỗi nhập liệu", "Tên sản phẩm không được để trống.");
                return;
            }
            if (startPriceStr.isEmpty()) {
                AlertUtils.showError("Lỗi nhập liệu", "Giá khởi điểm không được để trống.");
                return;
            }
            if (startTimeStr.isEmpty()) {
                AlertUtils.showError("Lỗi nhập liệu", "Thời gian bắt đầu không được để trống.");
                return;
            }
            if (durationStr.isEmpty()) {
                AlertUtils.showError("Lỗi nhập liệu", "Thời lượng đấu giá không được để trống.");
                return;
            }

            long startPrice;
            try {
                startPrice = Long.parseLong(startPriceStr);
                if (startPrice <= 0) {
                    AlertUtils.showError("Lỗi nhập liệu", "Giá khởi điểm phải lớn hơn 0.");
                    return;
                }
            } catch (NumberFormatException e) {
                AlertUtils.showError("Lỗi nhập liệu", "Giá khởi điểm không hợp lệ.");
                return;
            }

            java.time.LocalDateTime newStartTime;
            try {
                newStartTime = java.time.LocalDateTime.parse(startTimeStr, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e) {
                AlertUtils.showError("Lỗi nhập liệu", "Thời gian bắt đầu không đúng định dạng yyyy-MM-dd HH:mm:ss.");
                return;
            }

            int durationMinutes;
            try {
                durationMinutes = Integer.parseInt(durationStr);
                if (durationMinutes <= 0) {
                    AlertUtils.showError("Lỗi nhập liệu", "Thời lượng phải lớn hơn 0 phút.");
                    return;
                }
            } catch (NumberFormatException e) {
                AlertUtils.showError("Lỗi nhập liệu", "Thời lượng đấu giá phải là số nguyên hợp lệ.");
                return;
            }

            // Gửi dữ liệu cập nhật lên server
            Map<String, Object> payload = new HashMap<>();
            payload.put("auctionId", auction.getId());
            payload.put("itemName", name);
            payload.put("description", desc);
            payload.put("startPrice", startPrice);
            payload.put("newStartTime", startTimeStr.replace(" ", "T")); // đổi sang dạng ISO để dễ parse ở Server
            payload.put("durationMinutes", durationMinutes);

            NetworkService.sendMessage("EDIT_AUCTION", payload);
        });
    }
}