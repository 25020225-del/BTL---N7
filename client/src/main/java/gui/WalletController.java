package gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox; // Import đúng layout gốc

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * WalletController acts as both a Controller and a Custom Node (VBox).
 */
public class WalletController extends VBox {
    private Runnable onReturnAction;

    @FXML private Label lblTotalBalance;
    @FXML private Label lblFrozenBalance;
    @FXML private TextField txtDepositAmount;

    @FXML private TableView<Transaction> tableTransactions;
    @FXML private TableColumn<Transaction, String> colDate;
    @FXML private TableColumn<Transaction, String> colType;
    @FXML private TableColumn<Transaction, Long> colAmount;
    @FXML private TableColumn<Transaction, String> colStatus;
    @FXML private TableColumn<Transaction, String> colNote;

    private long currentBalance = 0L;
    private ObservableList<Transaction> transactionData = FXCollections.observableArrayList();

    public WalletController() {
        // Load FXML as a Custom Control
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/gui/WalletView.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load WalletView.fxml", e);
        }
    }

    @FXML
    public void initialize() {
        // Initialize table columns
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colNote.setCellValueFactory(new PropertyValueFactory<>("note"));

        tableTransactions.setItems(transactionData);
        updateBalanceUI();
    }

    public void setOnReturnAction(Runnable action) {
        this.onReturnAction = action;
    }

    @FXML
    private void handleReturn() {
        if (onReturnAction != null) {
            onReturnAction.run();
        }
    }

    @FXML
    private void handleDeposit() {
        try {
            String text = txtDepositAmount.getText().replaceAll("[^\\d]", "");
            long amount = Long.parseLong(text);
            if (amount <= 0) throw new Exception();

            currentBalance += amount;

            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            transactionData.add(0, new Transaction(now, "Nạp tiền", amount, "Completed", "Nạp tiền hệ thống"));

            updateBalanceUI();
            txtDepositAmount.clear();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thành công");
            alert.setHeaderText(null);
            alert.setContentText("Đã nạp " + amount + " VND thành công!");
            alert.showAndWait();

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText(null);
            alert.setContentText("Vui lòng nhập số tiền hợp lệ!");
            alert.showAndWait();
        }
    }

    private void updateBalanceUI() {
        lblTotalBalance.setText(String.format("%d N VND", currentBalance));
        lblFrozenBalance.setText("0 N VND");
    }

    @FXML
    private void addQuickAmount(javafx.event.ActionEvent event) {
        Button btn = (Button) event.getSource();
        String text = btn.getText().replace("+", "").replace("k", "000").replace("M", "000000");
        txtDepositAmount.setText(text);
    }
}