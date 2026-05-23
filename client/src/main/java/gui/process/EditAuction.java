package gui.process;

import client.network.NetworkService;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.HashMap;
import java.util.Map;

public class EditAuction {
    public static void edit(String currentAuctionId) {
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Edit Auction");
        dialog.setHeaderText("Update details for auction: " + currentAuctionId);

        ButtonType saveButtonType = new ButtonType("Save Changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        TextArea descField = new TextArea();
        descField.setPrefRowCount(3);
        TextField priceField = new TextField();

        grid.add(new Label("Item Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Description:"), 0, 1);
        grid.add(descField, 1, 1);
        grid.add(new Label("Starting Price:"), 0, 2);
        grid.add(priceField, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                Map<String, String> result = new HashMap<>();
                result.put("auctionId", currentAuctionId);
                result.put("itemName", nameField.getText());
                result.put("description", descField.getText());
                result.put("startPrice", priceField.getText());
                return result;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(data -> {
            if (data.get("itemName").isEmpty() || data.get("startPrice").isEmpty()) {
                AlertUtils.showError("Validation Error", "Name and Price cannot be empty.");
                return;
            }
            try {
                Double.parseDouble(data.get("startPrice"));
                NetworkService.sendMessage("EDIT_AUCTION", data);
            } catch (NumberFormatException e) {
                AlertUtils.showError("Validation Error", "Invalid price format.");
            }
        });
    }
}
