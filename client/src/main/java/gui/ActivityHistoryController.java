package gui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.util.Callback;

public class ActivityHistoryController {

    @FXML private TableView<Object> tableBidHistory;
    @FXML private TableView<Object> tableSellHistory;
    @FXML private TableColumn<Object, String> colBidStatus;
    @FXML private TableColumn<Object, String> colSellStatus;
    @FXML private TableColumn<Object, String> colBidDate;
    @FXML private TableColumn<Object, String> colBidItem;
    @FXML private TableColumn<Object, Double> colBidAmount;

    @FXML
    public void initialize() {
        colBidDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colBidItem.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colBidAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colBidStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        // tạo màu cho trạng thái lúc đấu giá
        setupStatusColumn(colBidStatus);
        // tạo màu cho trạng thái lúc sell
        setupStatusColumn(colSellStatus);

        loadDataFromServer();
    }

    private void setupStatusColumn(TableColumn<Object, String> column) {
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<Object, String> call(TableColumn<Object, String> param) {
                return new TableCell<>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setGraphic(null);
                            setText(null);
                        } else {
                            Label statusLabel = new Label(item.toUpperCase());
                            statusLabel.getStyleClass().add("status-label");
                            if (item.equalsIgnoreCase("Won") || item.equalsIgnoreCase("Success")) {
                                statusLabel.getStyleClass().add("status-won");
                            } else if (item.equalsIgnoreCase("Lost") || item.equalsIgnoreCase("Failed")) {
                                statusLabel.getStyleClass().add("status-lost");
                            } else if (item.equalsIgnoreCase("Ongoing")) {
                                statusLabel.getStyleClass().add("status-ongoing");
                            } else if (item.equalsIgnoreCase("Sold")) {
                                statusLabel.getStyleClass().add("status-sold");
                            } else {
                                statusLabel.getStyleClass().add("status-pending");
                            }

                            HBox container = new HBox(statusLabel);
                            container.setStyle("-fx-alignment: CENTER;");
                            setGraphic(container);
                        }
                    }
                };
            }
        });
    }

    private void loadDataFromServer() {
        // gửi lệnh lâý thông tin từ server, lễ hoặc khoa viết logic nhé
    }

    @FXML
    private void handleBack(javafx.event.ActionEvent event) {
        // quay lai man hinh user, tôi chả thấy trang chủ của bidder đâu, ae xoá rồi à ?
    }
}